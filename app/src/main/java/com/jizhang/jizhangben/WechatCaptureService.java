package com.jizhang.jizhangben;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WechatCaptureService extends NotificationListenerService {

    private static final String WECHAT_PACKAGE = "com.tencent.mm";
    private static final String ALIPAY_PACKAGE = "com.eg.android.AlipayGphone";
    private static final Pattern AMOUNT_PATTERN =
            Pattern.compile("(?:[¥￥]\\s*(\\d+(?:\\.\\d{1,2})?)|(\\d+(?:\\.\\d{1,2})?)\\s*元)");
    private static final Pattern FALLBACK_NUMBER_PATTERN = Pattern.compile("\\d+(?:\\.\\d{1,2})?");

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (!CaptureStore.isEnabled(this)) return;
        String pkg = sbn.getPackageName();

        Notification notification = sbn.getNotification();
        if (notification == null) return;

        String title = String.valueOf(notification.extras.getCharSequence(Notification.EXTRA_TITLE, ""));
        String text = String.valueOf(notification.extras.getCharSequence(Notification.EXTRA_TEXT, ""));
        String bigText = String.valueOf(notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT, ""));
        String subText = String.valueOf(notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT, ""));
        String all = title + "\n" + text + "\n" + bigText + "\n" + subText;

        String platform;
        if (WECHAT_PACKAGE.equals(pkg)) {
            platform = "wechat";
        } else if (ALIPAY_PACKAGE.equals(pkg)) {
            platform = "alipay";
        } else if (all.contains("交易提醒") && isExpensePayment(all)) {
            platform = "remind";
        } else {
            return;
        }

        saveDiagnostic(platform, title, text, "收到" + platformName(platform) + "通知");

        if (!isExpensePayment(all)) {
            saveDiagnostic(platform, title, text, "已忽略：不是支付成功通知或属于退款/到账");
            return;
        }

        double amount = parseAmount(all);
        if (amount <= 0) {
            saveDiagnostic(platform, title, text, "已忽略：通知中没有识别到金额");
            return;
        }

        String merchant = findMerchant(all, title, platform);
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
        String time = new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date());
        JSONObject record = new JSONObject();
        try {
            record.put("date", date);
            record.put("time", time);
            record.put("type", "expense");
            record.put("amount", Math.round(amount * 100) / 100.0);
            record.put("note", merchant);
            record.put("category", "");
            record.put("platform", platform);
            record.put("ts", System.currentTimeMillis());
        } catch (Exception ignored) {
            return;
        }
        boolean appended = CaptureStore.append(this, record);
        saveDiagnostic(platform, title, text, appended ? "已识别并暂存" : "重复通知，已跳过");
    }

    private static boolean isExpensePayment(String all) {
        if (all.contains("退款") || all.contains("已退款") || all.contains("退款成功")) return false;
        if (all.contains("已到账") || all.contains("到账提醒") || all.contains("收到转账") || all.contains("收到红包")) {
            return false;
        }
        if (all.contains("收入") || all.contains("入账")) return false;
        if (all.contains("收款") && !all.contains("已支付") && !all.contains("支付成功")
                && !all.contains("付款成功") && !all.contains("支付凭证") && !all.contains("已付款")) {
            return false;
        }
        return all.contains("交易提醒")
                || all.contains("消费")
                || all.contains("支出")
                || all.contains("支付宝")
                || all.contains("微信支付")
                || all.contains("已支付")
                || all.contains("支付成功")
                || all.contains("付款成功")
                || all.contains("支付凭证")
                || all.contains("已付款");
    }

    private static double parseAmount(String all) {
        Matcher matcher = AMOUNT_PATTERN.matcher(all);
        if (matcher.find()) {
            String amountText = matcher.group(1);
            if (amountText == null || amountText.isEmpty()) amountText = matcher.group(2);
            if (amountText != null && !amountText.isEmpty()) {
                try {
                    double value = Double.parseDouble(amountText);
                    if (value > 0 && value < 100000000) return value;
                } catch (Exception ignored) {
                }
            }
        }
        Matcher fallback = FALLBACK_NUMBER_PATTERN.matcher(all);
        while (fallback.find()) {
            try {
                double value = Double.parseDouble(fallback.group());
                if (value > 0 && value < 100000000) return value;
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    private static String findMerchant(String all, String title, String platform) {
        String[] lines = all.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("收款方") || trimmed.startsWith("商户") || trimmed.startsWith("商家")) {
                int index = trimmed.indexOf('：');
                if (index < 0) index = trimmed.indexOf(':');
                if (index >= 0 && index < trimmed.length() - 1) {
                    String value = trimmed.substring(index + 1).trim();
                    if (!value.isEmpty()) return value;
                }
            }
        }
        if (title != null
                && !title.contains("微信支付")
                && !title.contains("支付宝")
                && !title.contains("微信团队")
                && !title.contains("腾讯")
                && !title.contains("支付成功")
                && !title.contains("付款")
                && !title.contains("支付")) {
            String value = title.trim();
            if (!value.isEmpty()) return value;
        }
        return platformName(platform);
    }

    private static String platformName(String platform) {
        if ("alipay".equals(platform)) return "支付宝";
        if ("wechat".equals(platform)) return "微信";
        return "交易提醒";
    }

    private void saveDiagnostic(String platform, String title, String text, String decision) {
        JSONObject diag = new JSONObject();
        try {
            diag.put("platform", platform);
            diag.put("title", cut(title, 120));
            diag.put("text", cut(text, 240));
            diag.put("decision", decision);
            diag.put("ts", System.currentTimeMillis());
        } catch (Exception ignored) {
            return;
        }
        CaptureStore.setDiagnostic(this, diag);
    }

    private static String cut(String value, int max) {
        String s = String.valueOf(value == null ? "" : value);
        return s.length() <= max ? s : s.substring(0, max);
    }
}

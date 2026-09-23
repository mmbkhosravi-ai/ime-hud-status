# IME-HUD Status

نمایش قیمت صندوق عیار در نوار وضعیت اندروید.

## نحوه کار

- اپ به `http://127.0.0.1:5056/api/notif/data` (سرور Flask محلی) وصل می‌شود
- هر ۵ ثانیه قیمت را می‌خواند
- سه رقم اول قیمت را به‌عنوان small icon در نوار وضعیت نشان می‌دهد
- رنگ عدد بر اساس تغییر روزانه (سبز/قرمز)

## ساخت APK

اپ به‌صورت خودکار با GitHub Actions ساخته می‌شود:

1. Push کنید به `main`
2. Actions → Build APK
3. دانلود `IME-HUD-Status-APK`

## پیش‌نیازها

- Termux:API و سرور Flask روی همان گوشی در حال اجرا
- دسترسی اینترنت (فقط localhost)

## مجوزها

- `INTERNET` — اتصال به 127.0.0.1
- `FOREGROUND_SERVICE` — سرویس مداوم
- `POST_NOTIFICATIONS` — نمایش نوتیف
- `RECEIVE_BOOT_COMPLETED` — شروع خودکار بعد از بوت

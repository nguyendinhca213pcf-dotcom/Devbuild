# DevBuild – ứng dụng Android

App Android (Kotlin + Jetpack Compose) dùng REST API `http://devbuild.getenjoyment.net/devbuild/api/v1/`.

Chức năng: đăng nhập / đăng ký (có xác minh email, chuyển đổi English ⇄ Tiếng Việt, mặc định English), xem – tìm kiếm – lọc – sắp xếp code, xem chi tiết,
gắn sao, bình luận, đăng / sửa / xóa code, tin tức, bảng xếp hạng, thông báo, hồ sơ, đăng xuất.

## Cập nhật API v2
- Thư (hộp thư, soạn, trả lời, xóa), Nhiệm vụ (nhận, nhận thưởng, tự gửi heartbeat), Cửa hàng khung (mua, dùng, bỏ), Tài khoản (avatar, tên, mật khẩu, phiên đăng nhập).
- Đăng nhập bằng Google / GitHub / Discord qua cầu nối web (`devbuild://auth`).
- **Tất cả API** (tab Tôi): chạy được cả 185 endpoint, gồm khu vực Admin (chỉ hiện khi tài khoản là staff). Form tham số được sinh từ mã nguồn PHP.

## Cách lấy file APK

### Cách 1 – GitHub Actions (không cần cài gì)
1. Tạo repo GitHub, đẩy toàn bộ thư mục này lên (nhánh `main`).
2. Vào tab **Actions** → workflow **Build APK** chạy tự động (hoặc bấm *Run workflow*).
3. Khi xong, tải artifact **DevBuild-APK** → giải nén ra `app-release.apk` → cài lên điện thoại.

### Cách 2 – Android Studio
1. Cài Android Studio (bản mới), chọn **Open** thư mục này, đợi Gradle sync (dùng Gradle 8.7, JDK 17).
2. **Build ▸ Build APK(s)** → file nằm ở `app/build/outputs/apk/release/app-release.apk`.

### Cách 3 – dòng lệnh
```
gradle assembleRelease      # cần Gradle 8.7, JDK 17, Android SDK 34
```

APK được ký bằng debug key nên cài trực tiếp được. Muốn đưa lên Google Play, hãy tạo keystore riêng.
Đổi địa chỉ máy chủ ở hằng số `SITE_DIR` trong `Core.kt`.

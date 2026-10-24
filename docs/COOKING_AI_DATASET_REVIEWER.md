# Rà soát ảnh Cooking AI — không cần viết JSON

Dataset hiện có **109 ảnh Wiki**. Khi bàn giao công cụ: **0 ảnh được người dùng xác minh**, 0/109 đã rà. Công cụ chỉ chạy trên máy, không gọi AI, OpenAI hoặc dịch vụ ngoài; không tự chạy training.

## Mở giao diện

Chạy PowerShell tại thư mục dự án:

```powershell
Set-Location C:\Users\admin\AndroidStudioProjects\reCIme
& .\backend\.venv\Scripts\python.exe -m cooking_ai.reviewer serve --open-browser
```

Mở [http://127.0.0.1:8770](http://127.0.0.1:8770). Nếu server đã chạy, chỉ mở link; không chạy thêm cùng cổng. Dừng server bằng Ctrl+C trong cửa sổ đã mở nó. Công cụ dùng FastAPI/uvicorn có sẵn trong môi trường backend; không cần key hay cài model. Chỉ khi manifest Wiki mặc định **chưa tồn tại**, lệnh mới gọi exporter có sẵn để chuẩn bị dữ liệu.

## Kiểm tra từng ảnh

1. Xem ảnh lớn, tên hiện tại, nguồn, tác giả, license và bằng chứng quyền. Có thể bấm nguồn để tự đối chiếu; công cụ không quyết định quyền thay bạn.
2. Chọn **Đúng nhãn / Sai nhãn / Không chắc / Bỏ qua**. Sai, không chắc và bỏ qua đều bị loại khỏi export verified. Nếu biết chắc tên đúng, sửa tên rồi chọn Đúng nhãn; không đoán tên thay thế.
3. Thêm từng nguyên liệu **thấy trực tiếp**, kèm mô tả bằng chứng (ví dụ “cọng hành xanh trên mặt”). Không biết thì không thêm. Ghi điều chưa xác định trong ô tương ứng. Công cụ luôn xuất lượng/unit ẩn là `null`, không suy ra bước nấu từ ảnh.
4. Chờ thông báo **Đã lưu trên máy này**. Chỉ khi đã kiểm tra xong, tự tích **hai ô xác nhận quyền sử dụng và nhãn**. Đây là điều kiện đưa ảnh Đúng nhãn vào tập verified. Sửa nội dung sẽ bỏ xác nhận nhãn và cần bạn xác nhận lại.
5. Dùng Trước/Sau hoặc chọn số ảnh. Nếu mất kết nối/lưu lỗi, công cụ giữ nội dung trên màn hình và chặn chuyển ảnh; khôi phục server rồi bấm Lưu ngay. Khi có xung đột hai tab, sao chép nội dung đang sửa trước khi tải lại; không ghi đè bản của tab khác.

Mỗi thao tác tự lưu nháp vào `cooking_ai/data/private/reviewer/wiki/reviewer.sqlite3`. Có thể đóng rồi chạy lại cùng lệnh để tiếp tục tại ảnh gần nhất. Chờ lưu thành công trước khi đóng; nội dung chưa gửi được không bảo đảm tồn tại sau khi trình duyệt bị tắt cưỡng bức. Không xóa thư mục state. Không cần nhập annotation JSON.

## Export và Validate

- **Validate kết quả**: chạy importer/validator thật trên bản sao của các ảnh đã xác minh, báo PASS/FAIL và số ảnh bị loại. Không thay bản export đã công bố.
- **Export mẫu đã xác minh**: tạo một phiên mới gồm `annotations.jsonl`, `manifest.jsonl`, `media/`, `validation_report.json` bên dưới `cooking_ai/data/private/real/reviewer-<hash>/exports/<id>/`. `latest.json` ở thư mục `reviewer-<hash>` trỏ tới phiên export thành công mới nhất. Giao diện hiển thị đường dẫn đầy đủ.
- Validate bằng PowerShell, kể cả khi đã đóng giao diện:

```powershell
& .\backend\.venv\Scripts\python.exe -m cooking_ai.reviewer validate
```

Lệnh này kiểm tra nháp hiện tại bằng cùng pipeline, lưu phiên kiểm tra riêng dưới `checks/` khi có mẫu verified; không cần tự chọn manifest. `passed`/`failed` là số mẫu vượt/không vượt kiểm tra kỹ thuật. `verified` là số ảnh bạn đã chủ động xác nhận, `reviewed` là số ảnh đã phân loại Đúng/Sai/Không chắc. Chưa xác minh ảnh nào sẽ báo 0 PASS, 0 FAIL, 0 export và không tạo manifest rỗng; đây không phải dataset sẵn sàng training.

## Khi nào đã rà đủ 109 ảnh?

Tiến độ phải ghi **109/109 đã rà**, **0 chưa rà**, **0 bỏ qua**; CLI báo `all_reviewed: true`. Sai/Không chắc vẫn tính là đã rà nhưng không nằm trong verified. Kiểm tra thêm số **đã xác minh** theo mong muốn; không cần ép toàn bộ ảnh thành nhãn đúng. Validate chỉ kiểm tra schema, hash, permission và split, **không đo độ chính xác nhãn**.

Manifest/ảnh Wiki gốc và artifact training được giữ nguyên. Export giữ source group, split, tác giả/license/provenance và hash; không chia lại nguồn giữa train/test. Dùng manifest export làm bản dữ liệu ảnh đã rà; không nối thêm bản sao Wiki cũ vào tập training/evaluation. Chưa có ảnh verified thì tiếp tục rà, không chạy Qwen. Sau khi bạn hoàn thành, Codex có thể kiểm tra kết quả và chuẩn bị bước tiếp theo.

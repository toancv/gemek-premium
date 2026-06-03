# Hướng dẫn sử dụng — Gemek Premium
## Hệ thống Quản lý Chung cư

**Phiên bản:** 1.0 · **Cập nhật:** 2026-06-03

---

## Tổng quan

Hệ thống có **hai cổng truy cập riêng biệt**:

| Cổng | Địa chỉ | Dành cho | Tối ưu trên |
|------|---------|----------|-------------|
| **Admin Portal** | `http://<địa chỉ server>` (cổng 80) | Ban quản lý, kỹ thuật viên, thành viên ban giám sát | Máy tính để bàn |
| **Resident Portal** | `http://<địa chỉ server>:81` | Cư dân | Điện thoại di động |

**Các vai trò trong hệ thống:**

| Vai trò | Mô tả |
|---------|-------|
| **ADMIN** | Ban quản lý — toàn quyền trên tất cả chức năng |
| **TECHNICIAN** | Kỹ thuật viên nội bộ — xử lý yêu cầu được phân công |
| **BOARD_MEMBER** | Thành viên ban giám sát — xem báo cáo, chỉ đọc |
| **RESIDENT** | Cư dân — dùng cổng riêng |

> **Lưu ý:** Tài khoản do ban quản lý (ADMIN) tạo. Cư dân không tự đăng ký được.

---

---

# PHẦN 1 — CỔNG QUẢN LÝ (Admin Portal)

Truy cập: `http://<địa chỉ server>` · Trình duyệt desktop khuyến nghị.

---

## 1. Đăng nhập

**Vai trò:** ADMIN, TECHNICIAN, BOARD_MEMBER

Trang đăng nhập hiển thị logo **Gemek Premium — Admin Portal**.

**Các bước:**
1. Mở trình duyệt, truy cập địa chỉ Admin Portal.
2. Nhập **Email** và **Password**.
3. Nhấn nút **Sign in**.
4. Nếu sai thông tin, màn hình hiện thông báo lỗi đỏ ngay bên dưới.

Sau khi đăng nhập thành công, hệ thống chuyển thẳng vào trang **Dashboard**.

**Đăng xuất:** Nhấn **Sign out** ở góc dưới cột menu bên trái.

---

## 2. Dashboard — Tổng quan hệ thống

**Vai trò:** ADMIN, BOARD_MEMBER, TECHNICIAN

Trang chủ sau khi đăng nhập. Hiển thị 4 chỉ số nhanh ở hàng trên:

| Thẻ | Nội dung |
|-----|---------|
| **Open Tickets** | Số yêu cầu đang mở; dòng phụ ghi số đang xử lý (in progress) |
| **SLA Breached** | Số yêu cầu trễ hạn — cần xử lý ngay |
| **Bookings This Month** | Số lượt đặt tiện ích tháng này; dòng phụ ghi số đang chờ duyệt |
| **Expiring Contracts** | Số hợp đồng hết hạn trong 30 ngày tới |

Phía dưới có hai bảng:
- **Apartments** — Tổng số căn hộ, số đang có người (Occupied), số trống (Available), tỷ lệ lấp đầy (Occupancy Rate).
- **Tickets by Category** — Số yêu cầu đang mở theo từng loại.

---

## 3. Apartments — Quản lý căn hộ

**Vai trò:** ADMIN (toàn quyền), BOARD_MEMBER (chỉ xem)

Menu: **Apartments** (cột trái)

### 3.1 Xem danh sách căn hộ

Danh sách có thể lọc theo block, tầng, trạng thái, hoặc tìm theo số căn hộ. Mỗi dòng hiển thị: số căn hộ, block, diện tích, trạng thái (Occupied / Available / Maintenance), tên người liên hệ chính.

### 3.2 Thêm block (tòa nhà)

Block là đơn vị phân chia tòa. Phải tạo block trước khi thêm căn hộ.

**Các bước:** [TODO: kiểm tra nhãn nút trong ApartmentsPage — chưa đọc code trang này]
1. Truy cập menu **Apartments**.
2. Tìm nút tạo block mới.
3. Nhập **Name** (tên block, ví dụ: Block A) và **Description** (tùy chọn).
4. Lưu lại.

### 3.3 Thêm căn hộ mới

**Các bước:** [TODO: kiểm tra nhãn form trong ApartmentsPage]
1. Chọn **Apartments** → tìm nút tạo căn hộ mới.
2. Chọn block, nhập tầng (floor), số căn hộ (unit number), diện tích (m²).
3. Lưu. Hệ thống báo lỗi nếu số căn hộ đã tồn tại trong block đó.

### 3.4 Sửa thông tin căn hộ

Nhấn vào căn hộ cần sửa → chỉnh sửa thông tin → lưu.
Có thể cập nhật trạng thái (Occupied / Available / Maintenance).

---

## 4. Residents — Quản lý cư dân

**Vai trò:** ADMIN

Menu: **Residents** (cột trái)

### 4.1 Gán cư dân vào căn hộ

Trước khi gán, phải có tài khoản người dùng (tạo ở bước tạo tài khoản — xem mục 4.3).

**Các bước:** [TODO: kiểm tra nhãn form trong ResidentsPage]
1. Chọn menu **Residents** → tạo mới.
2. Chọn người dùng (User), căn hộ (Apartment).
3. Chọn loại cư dân: **OWNER** (chủ sở hữu) hoặc **TENANT** (người thuê).
4. Nhập ngày chuyển vào (Move-in date).
5. Đánh dấu **Is Primary Contact** nếu đây là đầu mối liên hệ chính của căn.
6. Lưu.

> Một người dùng chỉ được là cư dân hoạt động của **một** căn hộ tại một thời điểm.

### 4.2 Ghi nhận chuyển đi (move-out)

1. Tìm hồ sơ cư dân cần ghi nhận chuyển đi.
2. Thực hiện thao tác "move-out", nhập ngày chuyển đi.
3. Hệ thống lưu vào lịch sử cư dân của căn hộ.

### 4.3 Tạo tài khoản người dùng mới

[TODO: kiểm tra — chưa đọc UsersPage, không chắc có menu riêng hay gộp trong Residents]

Theo API, ADMIN có thể tạo tài khoản với vai trò: RESIDENT, TECHNICIAN, ADMIN, BOARD_MEMBER.

**Thông tin cần nhập:** Email (duy nhất), họ tên (Full Name), số điện thoại (tùy chọn), vai trò (Role), mật khẩu ban đầu.

### 4.4 Đặt lại mật khẩu cho người dùng

ADMIN có thể đặt lại mật khẩu cho bất kỳ tài khoản nào mà không cần biết mật khẩu cũ.

---

## 5. Tickets — Quản lý yêu cầu

**Vai trò:** ADMIN (toàn quyền), TECHNICIAN (xem + cập nhật trạng thái yêu cầu được giao), BOARD_MEMBER (chỉ xem)

Menu: **Tickets** (cột trái)

### 5.1 Các loại yêu cầu

Hệ thống dùng một mô-đun thống nhất cho tất cả loại yêu cầu, phân biệt bằng **Category**:

| Category hiển thị | Loại yêu cầu | Thời hạn xử lý (SLA) |
|-------------------|-------------|----------------------|
| **Maintenance & Repair** | Sửa chữa, bảo trì | 24 giờ |
| **Complaint** | Khiếu nại, phản ánh | 48 giờ |
| **Administrative** | Hành chính (thẻ từ, giấy tờ) | 72 giờ |
| **Suggestion / Feedback** | Góp ý, đề xuất | 7 ngày |
| **Other** | Khác | 48 giờ |

### 5.2 Trạng thái yêu cầu

| Trạng thái | Ý nghĩa |
|-----------|---------|
| **New** | Vừa tạo, chưa phân công |
| **Assigned** | Đã phân công cho nhân viên/nhà thầu |
| **In Progress** | Đang xử lý |
| **Done** | Đã hoàn thành |
| **Cancelled** | Đã hủy |

Yêu cầu trễ hạn SLA hiển thị ký hiệu ⚠ màu đỏ ở cột **SLA Deadline**.

### 5.3 Lọc và tìm kiếm yêu cầu

Trên trang **Tickets**, dùng hai bộ lọc thả xuống:
- **All Categories** → chọn loại yêu cầu cụ thể.
- **All Statuses** → chọn trạng thái cụ thể.

Kết quả phân trang, điều hướng bằng nút **Prev** / **Next**.

### 5.4 Phân công yêu cầu (ADMIN)

1. Nhấn vào yêu cầu để mở chi tiết.
2. [TODO: kiểm tra nhãn nút Assign trong TicketDetailPage admin]
3. Chọn nhân viên nội bộ (Assigned To User) **hoặc** nhà thầu (Assigned To Contractor) — chỉ chọn một.
   - Chỉ yêu cầu loại **Maintenance & Repair** mới được phân công cho nhà thầu.
4. Nhập ngày dự kiến (Scheduled Date, tùy chọn) và ghi chú.
5. Lưu. Nhân viên được phân công nhận thông báo trong ứng dụng.

### 5.5 Cập nhật trạng thái (ADMIN hoặc TECHNICIAN)

1. Mở chi tiết yêu cầu.
2. [TODO: kiểm tra nhãn nút cập nhật trạng thái trong TicketDetailPage]
3. Chọn trạng thái mới (In Progress / Done / Cancelled).
4. Nhập ghi chú xử lý nếu cần.
5. Lưu. Cư dân nộp yêu cầu nhận thông báo khi trạng thái thay đổi.

### 5.6 Tải ảnh đính kèm

Kỹ thuật viên và quản lý có thể tải ảnh theo ba giai đoạn:
- **BEFORE** — ảnh trước khi sửa
- **PROGRESS** — ảnh trong khi sửa
- **AFTER** — ảnh sau khi hoàn thành

Định dạng hỗ trợ: JPEG, PNG. Tối đa 10 MB/ảnh, 5 ảnh mỗi lần tải.

---

## 6. Contractors — Nhà thầu & Hợp đồng

**Vai trò:** ADMIN (toàn quyền), BOARD_MEMBER (chỉ xem)

Menu: **Contractors** (cột trái)

### 6.1 Quản lý nhà thầu

Danh sách nhà thầu theo chuyên ngành (CLEANING, ELECTRICAL, PLUMBING, v.v.). Mỗi nhà thầu có điểm đánh giá trung bình tự động tính từ phản hồi cư dân sau mỗi yêu cầu sửa chữa.

[TODO: kiểm tra nhãn form tạo nhà thầu trong ContractorsPage]

**Thông tin nhà thầu:** Tên công ty, người liên hệ, số điện thoại, email, mã số thuế, chuyên ngành.

### 6.2 Quản lý hợp đồng

Mỗi nhà thầu có thể có nhiều hợp đồng. Hợp đồng sắp hết hạn (trong 30 ngày) hiện cảnh báo trên Dashboard.

**Các bước tạo hợp đồng:**
1. Mở hồ sơ nhà thầu → tạo hợp đồng mới.
2. Nhập tên hợp đồng, phạm vi công việc (Scope), giá trị, ngày bắt đầu/kết thúc.
3. Tải file PDF hợp đồng (tùy chọn, tối đa 20 MB).
4. Lưu.

### 6.3 Ghi nhận thanh toán

Mỗi đợt thanh toán theo hợp đồng được ghi nhận riêng (ghi sổ, không phải lệnh chuyển tiền):
1. Mở hợp đồng → thêm thanh toán.
2. Nhập số tiền, ngày thanh toán, số tham chiếu (nếu có).

### 6.4 Lịch bảo trì định kỳ

Gắn lịch bảo trì định kỳ (hàng tháng, hàng quý, v.v.) vào hợp đồng. Hệ thống tự nhắc nhở khi đến kỳ hạn.

---

## 7. Announcements — Thông báo chung cư

**Vai trò:** ADMIN

Menu: **Announcements** (cột trái)

### 7.1 Tạo thông báo mới

[TODO: kiểm tra nhãn form trong AnnouncementsPage — chưa đọc code trang này]

**Thông tin cần nhập:**
- **Title** — tiêu đề thông báo.
- **Content** — nội dung.
- **Type** — loại: GENERAL (chung), URGENT (khẩn cấp), MAINTENANCE (bảo trì), AMENITY (tiện ích), EVENT (sự kiện).
- **Target Scope** — phạm vi gửi: ALL (toàn chung cư), BLOCK (một tòa), FLOOR (một tầng).
- Kênh gửi: **Push** (thông báo đẩy), **Email**, **SMS** — chọn một hoặc nhiều.
- **Publish Now** — đăng ngay hoặc lưu nháp để đăng sau.

### 7.2 Đăng thông báo đã lưu nháp

Mở thông báo nháp → thực hiện thao tác publish. Sau khi đăng, không thể chỉnh sửa nội dung.

### 7.3 Xem thống kê đọc

ADMIN xem được số cư dân đã đọc / chưa đọc từng thông báo.

---

## 8. Amenities — Quản lý tiện ích

**Vai trò:** ADMIN

Menu: **Amenities** (cột trái)

### 8.1 Thêm tiện ích mới

[TODO: kiểm tra nhãn form trong AmenitiesPage admin]

**Thông tin tiện ích:** Tên, mô tả, vị trí, sức chứa (Capacity), giờ mở cửa/đóng cửa, giới hạn đặt trong ngày mỗi cư dân (Max Daily Bookings Per Resident).

**Requires Approval:** Bật nếu mỗi lượt đặt cần admin phê duyệt trước khi xác nhận.

### 8.2 Duyệt lượt đặt tiện ích (nếu tiện ích yêu cầu phê duyệt)

1. Vào trang **Amenities** hoặc xem từ Dashboard (mục "pending approval").
2. Mở lượt đặt đang ở trạng thái **Pending**.
3. Chọn **Approve** (duyệt) hoặc **Reject** (từ chối, bắt buộc nhập lý do).

### 8.3 Hủy lượt đặt

ADMIN có thể hủy bất kỳ lượt đặt nào đang ở trạng thái Pending hoặc Approved (trước ngày đặt).

---

## 9. Parking — Quản lý bãi đỗ xe

**Vai trò:** ADMIN

Menu: **Parking** (cột trái)

### 9.1 Quản lý vị trí đỗ xe

**Các bước thêm vị trí mới:** [TODO: kiểm tra nhãn form trong ParkingPage admin]
1. Thêm vị trí, nhập số vị trí (ví dụ: B1-001), khu vực (Zone), loại xe (CAR / MOTORBIKE / BICYCLE).

### 9.2 Gán vị trí đỗ cho xe cư dân

1. Chọn vị trí đỗ còn trống.
2. Chọn xe đã đăng ký của cư dân, chọn căn hộ.
3. Nhập ngày bắt đầu, ngày kết thúc (tùy chọn), số thẻ đỗ xe (Parking Card Number).
4. Kết thúc gán vị trí khi cư dân trả chỗ.

### 9.3 Ghi nhận xe khách

1. Nhập biển số, tên chủ xe (tùy chọn), chọn căn hộ chủ (Host Apartment), mục đích thăm.
2. Ghi nhận xe ra bằng thao tác "exit".

---

## 10. Reports — Báo cáo & Thống kê

**Vai trò:** ADMIN, BOARD_MEMBER

Menu: **Reports** (cột trái)

[TODO: kiểm tra nhãn bộ lọc và tab trong ReportsPage — chưa đọc code trang này]

Hệ thống cung cấp các báo cáo sau:

**a) Báo cáo yêu cầu (Tickets)**
- Tổng số, đã hoàn thành, đang xử lý, tỷ lệ trễ SLA, điểm hài lòng trung bình.
- Có thể lọc theo khoảng thời gian, nhóm theo tháng, loại yêu cầu, trạng thái, hoặc người xử lý.

**b) Thống kê cư dân & căn hộ (Residents)**
- Tổng căn hộ, tỷ lệ lấp đầy, số cư dân chủ sở hữu / thuê, số cư dân trung bình mỗi căn.
- Có thể lọc theo block.

**c) Thống kê sử dụng tiện ích (Amenity Usage)**
- Số lượt đặt theo từng tiện ích, tỷ lệ sử dụng, ngày đông nhất.

**d) Hợp đồng sắp hết hạn (Contracts Expiring)**
- Danh sách hợp đồng sẽ hết hạn trong vòng 90 ngày tới (mặc định).

---

## 11. Thông báo trong ứng dụng (Notifications)

Biểu tượng chuông góc trên phải màn hình. Số đỏ hiển thị khi có thông báo chưa đọc.

- Nhấn chuông → danh sách thông báo mở ra.
- Thông báo chưa đọc có nền xanh nhạt.
- Nhấn **Mark all read** để đánh dấu tất cả đã đọc.
- "No notifications" khi không có thông báo mới.

---

---

# PHẦN 2 — CỔNG CƯ DÂN (Resident Portal)

Truy cập: `http://<địa chỉ server>:81` · Thiết kế cho điện thoại di động.

---

## 1. Đăng nhập

Trang đầu tiên sau khi mở ứng dụng.

**Các bước:**
1. Nhập **Email** và **Password** do ban quản lý cấp.
2. Nhấn **Sign in**.
3. Sau khi đăng nhập, màn hình chuyển về trang **Home**.

**Đăng xuất:** Nhấn nút **Out** góc trên phải màn hình.

> Nếu quên mật khẩu, liên hệ ban quản lý để đặt lại.

---

## 2. Home — Trang chủ

Menu dưới màn hình: **Home** (H)

Trang chủ gồm:
- **Thẻ chào:** Hiển thị tên cư dân và số điện thoại.
- **Active Tickets** — số yêu cầu đang mở; nhấn để vào trang Tickets.
- **Bookings** — tổng số lượt đặt tiện ích; nhấn để vào trang Bookings.
- **Announcements** — 3 thông báo mới nhất; nhấn **View all** để xem toàn bộ.

---

## 3. Tickets — Yêu cầu của tôi

Menu dưới màn hình: **Tickets** (T)

### 3.1 Xem danh sách yêu cầu

Danh sách chỉ hiển thị yêu cầu của căn hộ của bạn. Mỗi thẻ yêu cầu hiển thị: tiêu đề, trạng thái (màu sắc), loại yêu cầu và ngày tạo.

**Màu trạng thái:**
- Xanh dương — **NEW** (mới)
- Tím — **ASSIGNED** (đã phân công)
- Vàng — **IN PROGRESS** (đang xử lý)
- Xanh lá — **DONE** (hoàn thành)
- Xám — **CANCELLED** (đã hủy)

### 3.2 Gửi yêu cầu mới

1. Nhấn nút **+ New** góc trên phải.
2. Điền form **New Ticket**:
   - **Apartment ID** — mã căn hộ của bạn (UUID, do ban quản lý cung cấp) (\*)
   - **Category** — chọn loại yêu cầu: Maintenance & Repair / Complaint / Administrative / Suggestion & Feedback / Other
   - **Title** — tiêu đề ngắn gọn (\*)
   - **Description** — mô tả chi tiết (tùy chọn)
3. Nhấn **Submit** để gửi, hoặc **Cancel** để hủy.

Ban quản lý nhận thông báo ngay khi yêu cầu được gửi.

### 3.3 Xem chi tiết yêu cầu

Nhấn vào thẻ yêu cầu để xem chi tiết: người xử lý, lịch sử trạng thái, ảnh đính kèm.

### 3.4 Đánh giá sau khi hoàn thành

Sau khi yêu cầu chuyển sang trạng thái **DONE**, cư dân có thể đánh giá mức độ hài lòng (1–5 sao) và để lại nhận xét (tùy chọn). Mỗi yêu cầu chỉ đánh giá được một lần.

[TODO: kiểm tra nhãn nút đánh giá trong TicketDetailPage resident]

---

## 4. Amenities — Đặt tiện ích

Menu dưới màn hình: **Amenities** (A)

### 4.1 Xem danh sách tiện ích

Trang **Book Amenity** liệt kê các tiện ích của chung cư. Mỗi thẻ hiển thị: tên, vị trí, sức chứa, giờ hoạt động.

Nếu tiện ích có ghi **"Requires approval"** (màu cam) — lượt đặt cần ban quản lý phê duyệt trước khi có hiệu lực.

### 4.2 Đặt tiện ích

1. Nhấn nút **Book** trên tiện ích muốn đặt.
2. Điền form:
   - **Date** — ngày đặt (\*)
   - **Start** — giờ bắt đầu (\*)
   - **End** — giờ kết thúc (\*)
   - **Notes** — ghi chú (tùy chọn)
3. Nhấn **Confirm** để xác nhận hoặc **Cancel** để hủy bỏ.
4. Nếu thành công, màn hình hiện ✅ và thông báo **"Booking submitted!"**

**Lưu ý:**
- Không thể đặt trùng giờ với lượt đặt đã được duyệt.
- Mỗi cư dân chỉ được đặt tối đa một số lượt nhất định mỗi ngày theo quy định ban quản lý.
- Không được đặt ngày trong quá khứ.
- Thời gian đặt tối thiểu 30 phút; tối đa không quá 14 ngày tính từ hôm nay.

---

## 5. Bookings — Lịch đặt tiện ích của tôi

Menu dưới màn hình: **Bookings** (B)

[TODO: kiểm tra nhãn và hiển thị cụ thể trong MyBookingsPage — chưa đọc code trang này]

Hiển thị danh sách các lượt đặt tiện ích của bạn. Trạng thái có thể là:

| Trạng thái | Ý nghĩa |
|-----------|---------|
| **Pending** | Đang chờ ban quản lý duyệt |
| **Approved** | Đã được xác nhận |
| **Rejected** | Bị từ chối |
| **Cancelled** | Đã hủy |
| **Completed** | Đã qua ngày đặt |

**Hủy lượt đặt:** Cư dân có thể hủy lượt đặt đang ở trạng thái Pending hoặc Approved (trước ngày sử dụng).

---

## 6. Parking — Xe của tôi

Menu dưới màn hình: **Parking** (P)

[TODO: kiểm tra nội dung hiển thị trong ParkingPage resident — chưa đọc code trang này]

Cư dân xem thông tin xe đã đăng ký và vị trí đỗ xe được phân công. Việc đăng ký xe và phân công vị trí đỗ do ban quản lý thực hiện.

**Đăng ký xe mới:** Liên hệ ban quản lý hoặc thực hiện qua form đăng ký xe (nếu có trong trang này).

---

## 7. News — Thông báo chung cư

Menu dưới màn hình: **News** (N)

Hiển thị thông báo từ ban quản lý, chỉ hiển thị thông báo gửi tới toàn chung cư hoặc block/tầng của bạn.

**Thông báo chưa đọc** có nền xanh nhạt.

**Nhấn vào thông báo** để đọc nội dung đầy đủ. Hệ thống tự đánh dấu đã đọc.

---

## 8. Profile — Tài khoản

Menu dưới màn hình: **Profile** (Me)

[TODO: kiểm tra nhãn form trong ProfilePage — chưa đọc code trang này]

Cư dân xem thông tin cá nhân và đổi mật khẩu.

### 8.1 Đổi mật khẩu

**Các bước:**
1. Vào trang **Profile**.
2. Tìm mục đổi mật khẩu.
3. Nhập mật khẩu hiện tại (Current Password).
4. Nhập mật khẩu mới (New Password) — tối thiểu 8 ký tự, phải gồm chữ hoa, chữ thường, số, ký tự đặc biệt.
5. Xác nhận và lưu.

---

## 9. Thông báo trong ứng dụng (Notifications)

Biểu tượng chuông góc trên phải. Số đỏ hiển thị khi có thông báo chưa đọc.

- Nhấn chuông → danh sách thông báo mở ra.
- Thông báo chưa đọc nền xanh nhạt.
- Nhấn **Mark all read** để đánh dấu tất cả đã đọc.
- **"No notifications"** khi không có thông báo mới.

---

## Hỏi & Đáp nhanh

**Quên mật khẩu?**
→ Liên hệ ban quản lý để đặt lại. Cư dân không tự khôi phục được.

**Không thấy thông báo chung cư?**
→ Thông báo được lọc theo block/tầng của bạn. Nếu thông báo gửi cho block khác, bạn không thấy.

**Yêu cầu gửi rồi nhưng lâu chưa được xử lý?**
→ Kiểm tra trạng thái ở trang **Tickets**. Yêu cầu trễ SLA sẽ được hệ thống tự cảnh báo ban quản lý.

**Đặt tiện ích bị báo lỗi "time slot unavailable"?**
→ Khung giờ đó đã có người đặt trước. Chọn giờ khác hoặc ngày khác.

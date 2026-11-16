# Tài liệu plugin Synthworks

## 1. Tổng quan

Synthworks là plugin Kotlin cho Paper/Purpur, viết cho Minecraft 1.21.11. Plugin bổ sung hệ thống item custom và chế tạo theo công thức riêng:

- Người chơi mở menu Synthworks bằng cách right-click bàn Crafter được cấu hình.
- Recipe Book hiển thị công thức theo category/folder.
- Một recipe có thể yêu cầu blueprint. Dùng blueprint để mở khóa recipe cho người chơi.
- Người chơi đặt nguyên liệu vào các ô riêng, xác nhận để chế tạo; không sử dụng lưới crafting vanilla 3x3.
- OP có thể quản lý item, recipe và blueprint bằng `/synthworks editor`.

Item, recipe và dữ liệu mở khóa blueprint được lưu trong thư mục dữ liệu của plugin. Dữ liệu item/recipe cũng có thể cấu hình trực tiếp bằng YAML.

## 2. Yêu cầu và cài đặt

### Yêu cầu

- Java 21
- Paper hoặc Purpur 1.21.11
- Gradle 8.10 trở lên để build

### Build

Từ thư mục dự án:

```bash
gradle build
```

JAR đầu ra:

```text
build/libs/Synthworks-1.0.0.jar
```

Build đóng gói Kotlin Standard Library cùng plugin JAR, vì vậy không cần cài Kotlin hoặc chép thêm Kotlin runtime riêng vào server. Chép JAR vào thư mục `plugins/` của server rồi khởi động lại server. Khi chạy lần đầu, plugin tạo/copy các file mặc định:

```text
plugins/Synthworks/config.yml
```

Blueprint người chơi đã mở khóa được lưu theo UUID:

```text
plugins/Synthworks/players/<uuid>.yml
```

Địa chỉ các block Crafter được ghi vào `plugins/Synthworks/crafters.yml` sau khi đặt block đầu tiên.

Khi nâng cấp từ plugin cũ, Synthworks chép các file chưa tồn tại từ `plugins/Synthesis/` sang `plugins/Synthworks/` và không ghi đè file đã có. Thư mục cũ được giữ nguyên. Vị trí Crafter cũ trong `synthesizers.yml`, item và blueprint đã phát vẫn được nhận diện.

## 3. Tên, lệnh và quyền

Tên plugin: **Synthworks**.

Lệnh gốc là `/synthworks`, alias rút gọn là `/sw`.

| Lệnh | Quyền | Tác dụng |
|---|---|---|
| `/synthworks` hoặc `/sw help` | Không yêu cầu | Hiện trợ giúp |
| `/synthworks open <player>` | `synthworks.admin` | Mở menu cho người chơi online |
| `/synthworks editor` hoặc `/synthworks edit` | `synthworks.admin` | Mở editor quản trị |
| `/synthworks give item <player> <id> [amount]` | `synthworks.admin` | Cấp custom item |
| `/synthworks give crafter <player>` | `synthworks.admin` | Cấp block Crafter |
| `/synthworks give blueprint <player> <recipe_id>` | `synthworks.admin` | Cấp blueprint cho recipe |
| `/synthworks reload` | `synthworks.admin` | Nạp lại config, item và recipe |
| `/synthworks info` hoặc `/synthworks debug` | `synthworks.admin` | Hiển thị trạng thái plugin |

Quyền cũ `synthesis.admin` và `synthesis.use` vẫn được chấp nhận để không làm mất cấu hình quyền hiện có.

Mặc định, `synthworks.admin` dành cho OP; `synthworks.use` mặc định bật cho người chơi. Crafter chỉ mở menu nếu người chơi có quyền `synthworks.use` hoặc quyền cũ `synthesis.use`.

## 4. Luồng người chơi

### Mở menu

Chạy `/synthworks give crafter <player>` để nhận block Crafter, sau đó đặt block xuống. Block dùng model vanilla của Crafter và có particle enchant xung quanh khi người chơi ở gần; vị trí được lưu để còn hoạt động sau khi server restart. Đập block sẽ rơi lại item Synthworks Crafter. Vật liệu bàn được cấu hình mặc định là `CRAFTER`.

Particle chỉ được phát khi chunk chứa block đang được load và có người chơi trong `particle-player-range`. `custom-model-data` chỉ tác động lên item Crafter cầm/đặt; model block sau khi đặt vẫn là model Crafter vanilla. Muốn thay model block cần resource pack và giải pháp custom block riêng. Menu chính có:

- **Recipe Book** — duyệt các category và recipe.
- **Blueprint Storage** — xem các recipe người chơi đã mở khóa bằng blueprint.

Quản trị viên cũng có thể dùng `/synthworks open <player>` để mở menu cho người chơi.

Các ô không thao tác trong menu được lấp bằng glass pane màu xám. Nút xác nhận/lưu dùng glass pane xanh lá; nút đóng, hủy và quay lại dùng glass pane đỏ. Các ô nhập item và nguyên liệu được để trống để người chơi có thể đặt đồ.

### Duyệt và chọn recipe

Recipe Book nhóm công thức theo trường `category`. Tên category được lấy từ recipe và hiển thị thành folder. Ví dụ: `dtier`, `ctier`, `junk`, `salvage`, `refined`, `misc`. Nếu recipe cũ đang để category `misc` nhưng recipe ID có dạng `ctier_recipe_name`, nhóm sẽ được suy ra từ phần trước dấu `_` để tránh mọi recipe bị gom vào Misc.

- Recipe không cần blueprint có thể chọn ngay.
- Recipe yêu cầu blueprint vẫn xuất hiện nhưng bị khóa với người chơi chưa mở khóa.
- Recipe đã mở khóa xuất hiện trong Blueprint Storage.

### Chế tạo

Khi chọn recipe, GUI chế tạo dùng 4 hàng (36 slot) với bố cục:

```text
O O O O X O O O O
X X X X I X X X X
E E E E X E E E E
C X X X X X X F
```

- `O`: vật liệu yêu cầu của recipe, tối đa 8 slot.
- `X`: slot không sử dụng, được khóa.
- `I`: item đầu ra, tại slot giữa hàng thứ hai.
- `E`: ô trống để người chơi submit nguyên liệu.
- `B`: quay lại menu trước; `Q`: Quick Craft bằng nguyên liệu trong inventory; `C`: chế tạo thủ công bằng các ô `E`.

Mỗi ô `E` tương ứng với slot `O` cùng vị trí (từ trái qua phải, bỏ qua khe giữa). Số lượng trong stack nguyên liệu đã lưu là số lượng cần nộp.

Nhấn **C** để chế tạo thủ công. Plugin kiểm tra item trong từng slot phải tương tự nguyên liệu yêu cầu và có số lượng đủ. Số lượng yêu cầu là amount của stack nguyên liệu đã lưu trong recipe. Nút **Q** kiểm tra inventory và tự lấy nguyên liệu để chế tạo một lần; nếu thiếu nguyên liệu, inventory không bị thay đổi. Người chơi có thể nhấn Q nhiều lần để chế tạo thêm.

- Nếu nguyên liệu hợp lệ và đủ: plugin trừ đúng số lượng yêu cầu rồi đưa item đầu ra vào inventory.
- Nếu nguyên liệu sai hoặc không đủ: toàn bộ item đã đưa vào GUI được trả lại và GUI đóng.
- Nếu người chơi quay lại hoặc đóng GUI: item còn trong các ô đầu vào được trả lại.
- Nếu inventory không còn chỗ: item trả lại sẽ rơi cạnh người chơi.

Nguyên liệu phải được đặt đúng slot tương ứng với recipe; các slot không tự gom nhiều loại item hoặc đổi chéo vị trí. Có thể click hoặc kéo item từ inventory cá nhân vào các ô hợp lệ. Shift-click bị chặn để tránh chuyển item ngoài ý muốn; drag trong inventory cá nhân vẫn hoạt động.

### Blueprint

Blueprint dùng item Knowledge Book (model quyển sách công thức/knowledge book) và được plugin gắn dấu nhận dạng riêng. Nếu resource pack có model tùy chỉnh cho blueprint, đặt `settings.blueprint.custom-model-data` phù hợp. Right-click blueprint:

1. Kiểm tra recipe blueprint trỏ tới có tồn tại không.
2. Lưu recipe ID vào danh sách blueprint của UUID người chơi.
3. Trừ một blueprint khỏi tay người chơi khi mở khóa thành công.

Nếu recipe đã được mở khóa, dùng blueprint thêm lần nữa sẽ báo đã mở khóa và không tiêu thụ blueprint.

## 5. Editor quản trị

Chạy `/synthworks editor` trong game với quyền `synthworks.admin`. Menu chính gồm bốn khu vực:

1. Tạo custom item.
2. Tạo recipe.
3. Danh sách/quản lý recipe.
4. Danh sách/quản lý item.

### 5.1 Tạo và chỉnh sửa custom item

Mở **Create custom item**, đặt item mẫu vào ô giữa, rồi cấu hình các thuộc tính:

- **Block placement** — cho phép item custom được đặt thành block.
- **Vanilla crafting** — cho phép item custom tham gia công thức crafting vanilla.
- **Glint** — bật hiệu ứng enchantment lấp lánh.
- **Unbreakable** — đặt item thành không thể hư hỏng.
- **Durability** — giá trị damage hiện tại, không phải durability tối đa.
- **Attributes** — modifier thuộc tính Bukkit.
- **Display name** — tên hiển thị, có thể dùng mã màu legacy như `&6`.
- **Lore** — các dòng mô tả, nhập nhiều dòng bằng dấu `|`.
- **Max stack size** — số lượng stack tối đa; nhập `0` để dùng mặc định của vật liệu.

Các lựa chọn văn bản/giá trị nhập trong chat. Khi bấm một tùy chọn nhập liệu, GUI sẽ đóng để người chơi gửi câu trả lời qua chat; server không thể tự bật cửa sổ chat phía client bằng Bukkit API. Nhập `cancel` để hủy và quay lại editor. Item mẫu được giữ trong lúc nhập và được trả lại khi đóng editor chưa lưu. Khi sửa item có sẵn, ID được giữ nguyên và item template hiện tại được dùng làm mẫu.

Định dạng nhập attributes:

```text
ATTRIBUTE:amount:operation,...
```

Ví dụ:

```text
ARMOR:4:ADD_NUMBER
```

Có thể nhập nhiều attribute, phân cách bằng dấu phẩy. Nhập `-` để xóa toàn bộ attributes. Tên attribute và operation phải là giá trị hợp lệ trong Bukkit API của server.

Khi lưu item mới, editor đóng GUI và yêu cầu nhập đúng hai phần trong chat: `folder item_id`. Ví dụ `dtier copper_wire` sẽ được lưu thành ID `dtier_copper_wire`. Mỗi phần chỉ gồm chữ/số, `_` hoặc `-`. Item được ghi thành `custom_items/<folder>/<item_id>.yml` và nạp lại ngay.

### 5.2 Tạo và chỉnh sửa recipe

Mở **Create recipe**:

1. Chọn tối đa tám stack nguyên liệu trong các ô ingredient.
2. Chọn output từ danh sách custom item bằng nút **Choose output item**.
3. Đặt category, ví dụ `ctier` hoặc `refined`.
4. Bật/tắt tùy chọn **Requires blueprint**.
5. Nhấn **Save recipe**.

Lượng item trong từng stack ingredient được lưu làm số lượng cần để chế tạo. Khi tạo recipe mới, nhập ID theo dạng `folder recipe_name`, ví dụ `ctier tactical_vest`; hệ thống sẽ lưu recipe ID là `ctier_tactical_vest` và category là `ctier`. Khi sửa recipe, category có thể đổi từ nút Category trên menu.

Recipe đầu ra phải là custom item đã được đăng ký trong Synthworks. Ingredient có thể là item vanilla hoặc custom item. Recipe được lưu riêng trong `recipes/<folder>/<recipe_id>.yml`.

Recipe editor dùng layout tương tự GUI chế tạo: nguyên liệu được nhập ở hàng thứ ba, hàng đầu hiển thị preview cùng số lượng yêu cầu, output ở giữa. Nút Save và Back nằm ở hàng điều khiển dưới; category và yêu cầu blueprint có nút cấu hình riêng.

### 5.3 Quản lý item và recipe

**Manage items** liệt kê item đã đăng ký. Chọn item để:

- Mở editor để chỉnh sửa các thuộc tính.
- Chọn **Give item** để cấp một item vào inventory của OP đang quản lý.
- Xóa item khỏi danh sách cấu hình.

**Manage recipes** liệt kê recipe. Chọn recipe để:

- Mở editor sửa category, blueprint requirement, ingredient hoặc output.
- Cấp item đầu ra.
- Cấp blueprint tương ứng.
- Xóa recipe.

Xóa một item khỏi cấu hình không xóa item đã được phát cho người chơi. Recipe tham chiếu output không tồn tại sẽ bị bỏ qua khi nạp.

## 6. Cấu hình

### `config.yml`

Các thiết lập đang được sử dụng:

```yaml
settings:
  crafter:
    material: CRAFTER
    custom-model-data: 0
    particle: trial_spawner_detection_ominous
    particle-interval-ticks: 20
    particle-count: 12
    particle-player-range: 24
    particle-location:
      x: 0.5
      y: 1.0
      z: 0.5
    particle-delta:
      x: 0.2
      y: 0.0
      z: 0.2
    particle-speed: 0.08
  blueprint:
    custom-model-data: 0
```

- `settings.crafter.material`: vật liệu được hiển thị trong thông tin cấu hình; bàn Synthworks được nhận diện riêng bằng item đã đánh dấu và vị trí đã lưu. Crafter vanilla không bị chặn tương tác.
- `settings.crafter.custom-model-data`: custom model data trên item Crafter trước khi đặt; `0` dùng model vanilla.
- `settings.crafter.particle-interval-ticks`: chu kỳ particle.
- `settings.crafter.particle-count`: số hạt phát ra mỗi lần hiệu ứng chạy.
- `settings.crafter.particle-player-range`: chỉ hiển thị particle khi có người chơi ở trong phạm vi block.
- `settings.crafter.particle`: tên particle Bukkit; mặc định `trial_spawner_detection_ominous`.
- `settings.crafter.particle-location.x/y/z`: vị trí phát particle tính từ góc dưới của block Crafter; `1, 1, 1` tương ứng mỗi tọa độ cộng thêm một block.
- `settings.crafter.particle-delta.x/y/z`: độ lệch ngẫu nhiên theo từng trục quanh vị trí phát.
- `settings.crafter.particle-speed`: tốc độ hướng lên trên của particle.
- `settings.blueprint.custom-model-data`: custom model data cho blueprint; `0` nghĩa là không đặt model data.

Khi nâng cấp, khóa config cũ `settings.table` và `settings.synthesizer` được chuyển sang các khóa Crafter mới. Nếu vật liệu cũ là `SMITHING_TABLE`, giá trị mới được đặt thành `CRAFTER`. Phần `settings.items` và `settings.integrations.executable-items` hiện chưa được dùng; Synthworks không có adapter ExecutableItems trong phiên bản này.

### Item config theo folder

Aggregate `custom_items.yml` chỉ được dùng để di chuyển dữ liệu từ bản cũ; cấu hình mới dùng file riêng trong `custom_items/<folder>/`.

Ví dụ:

```yaml
material: COPPER_INGOT
name: "&6Copper Wire"
lore:
  - "&7Industrial component"
custom-model-data: 43001
amount: 1
max-stack-size: 64
unbreakable: false
glint: false
block-placement: false
vanilla-crafting: false
durability: 0
attributes: {}
```

Lưu ví dụ trên tại `plugins/Synthworks/custom_items/refined/refined_copper_wire.yml` và dùng ID `refined_copper_wire`. Folder được lấy từ phần đầu của item ID trước dấu `_`; mỗi item có file YAML riêng.

Các trường item gồm:

| Trường | Mô tả |
|---|---|
| `material` | Tên vật liệu Bukkit, không được là AIR |
| `name` | Tên hiển thị; hỗ trợ mã màu dạng `&` |
| `lore` | Danh sách dòng mô tả |
| `custom-model-data` | Model data tùy chọn |
| `amount` | Số lượng mặc định khi tạo item |
| `max-stack-size` | Giới hạn stack tùy chọn |
| `unbreakable` | Item không hư hỏng |
| `glint` | Hiệu ứng lấp lánh |
| `block-placement` | Cho phép đặt item thành block |
| `vanilla-crafting` | Cho phép dùng trong crafting vanilla |
| `durability` | Damage hiện tại |
| `attributes` | Attribute modifiers, theo tên enum Bukkit |

Item được tạo/lưu trong editor có thêm khóa `serialized-stack`: dữ liệu nhị phân Base64 do Paper `ItemStack.serializeAsBytes()` tạo ra. Khi nạp, Synthworks giải mã nguyên stack rồi mới ghi đè hai PDC key nhận dạng riêng của plugin. Cách này giữ metadata, enchantment, lore, custom model data, attribute/component data và PDC có trên item mẫu; các file item cũ không có `serialized-stack` vẫn tiếp tục được tạo theo các trường YAML hiện có.

Ví dụ cấu hình attributes:

```yaml
attributes:
  ARMOR:
    amount: 4.0
    operation: ADD_NUMBER
```

Tên item ID được chuẩn hóa thành chữ thường và chỉ chấp nhận `a-z`, `0-9`, `_`, `-`, `:`. Chạy `/synthworks reload` sau khi sửa YAML thủ công.

### Recipe config theo folder

Aggregate `recipes.yml` chỉ được dùng để di chuyển dữ liệu từ bản cũ; recipe mới dùng file riêng trong `recipes/<folder>/`.

Mỗi recipe có file YAML riêng trong `plugins/Synthworks/recipes/<category>/<recipe_id>.yml`, gồm:

- `category`
- `result`: custom item ID làm output
- `requires-blueprint`
- `ingredients.0` đến `ingredients.7`: Bukkit `ItemStack` đã serialize, bao gồm metadata và lượng stack (tối đa 8 ingredient)

Khuyến nghị tạo/sửa recipe bằng editor thay vì sửa cấu trúc `ItemStack` serialization bằng tay.

### Cấu trúc thư mục dữ liệu

Item được lưu riêng theo folder/category và ID:

```text
plugins/Synthworks/custom_items/<folder>/<item_id>.yml
```

Recipe được lưu theo folder/category và ID:

```text
plugins/Synthworks/recipes/<folder>/<recipe_id>.yml
```

Khi khởi động hoặc reload, dữ liệu cũ trong `custom_items.yml` và `recipes.yml` được chuyển thành các file riêng trong folder. File cũ được giữ lại rỗng sau khi toàn bộ entry đã chuyển thành công để dễ đối chiếu; file riêng mới là dữ liệu được nạp sau đó.

### Quick Craft và blueprint

- Recipe không yêu cầu blueprint có thể Quick Craft ngay.
- Recipe yêu cầu blueprint chỉ Quick Craft được sau khi đã mở khóa blueprint **và chế tạo thủ công thành công ít nhất một lần**.
- Lần chế tạo thủ công đầu tiên sau khi mở khóa được lưu theo UUID; nếu chưa chế tạo thủ công, Q báo cần craft bằng C trước. Từ lần sau Q dùng được và trạng thái vẫn còn sau khi server restart.
- Quick Craft lấy đúng lượng từng nguyên liệu từ inventory người chơi, tạo một output mỗi lần nhấn và không tiêu thụ gì nếu thiếu nguyên liệu.

## 7. Nhận dạng item và bảo vệ crafting

Custom item do Synthworks tạo được gắn PersistentDataContainer keys:

- `synthworks:synthworks_item` — dấu nhận dạng Synthworks.
- `synthworks:item_id` — ID cấu hình.

Blueprint sử dụng key riêng `synthworks:blueprint_recipe` để lưu recipe ID. Crafter sử dụng `synthworks:crafter_item`. Để tương thích, plugin vẫn đọc các key cũ trong namespace `synthesis`, gồm cả dấu Synthesizer.

Plugin áp dụng hai giới hạn trên custom item:

- Nếu `block-placement` tắt, hủy sự kiện đặt block.
- Nếu `vanilla-crafting` tắt, chặn custom item tham gia crafting vanilla.

Blueprint và item Synthworks phụ thuộc vào persistent data marker; chỉ đổi tên hiển thị/lore không làm thay đổi ID nội bộ.

## 8. Giới hạn của phiên bản hiện tại

- Mỗi recipe có tối đa 8 loại/slot nguyên liệu; không có số lượng output tùy chỉnh trong editor, output mặc định là một item được tạo từ định nghĩa item.
- Recipe Book, danh sách recipe/item trong editor và output picker hiển thị tối đa 45 mục mỗi menu.
- Nguyên liệu synthesis được so khớp theo item stack metadata và phải nằm đúng slot; chưa có nhóm nguyên liệu thay thế hoặc recipe shapeless.
- Blueprint mặc định là Knowledge Book có custom model data tùy chọn; chưa có hệ thống hình ảnh/model tích hợp riêng.
- Không có tích hợp runtime với ExecutableItems.
- Lệnh chat nhập dữ liệu editor sử dụng `AsyncPlayerChatEvent` hiện đang deprecated trên Paper mới; có thể cần chuyển sang chat event API mới khi nâng cấp Paper API.
- Dự án hiện chưa có automated test suite.

## 9. Kiểm tra build

Phiên bản triển khai đã được biên dịch bằng Java 21 và Gradle 9.8.2. Lệnh `gradle build` hoàn tất thành công; build xác nhận mã Kotlin biên dịch và plugin JAR được đóng gói. Chưa có test tự động để xác nhận hành vi trên server thực tế.

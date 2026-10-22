"""Native Material redesign; output XML is always expanded for Layout Editor."""
from pathlib import Path
from xml.dom import minidom,Node
from xml.sax.saxutils import quoteattr
ROOT=Path(__file__).resolve().parents[1]/'app/src/main/res'

def write(name,source):
    doc=minidom.parseString(source)
    def render(node,depth=0):
        indent='    '*depth;lines=[indent+'<'+node.tagName]
        for name,value in node.attributes.items():lines.append(indent+'    '+name+'='+quoteattr(value))
        children=[c for c in node.childNodes if c.nodeType==Node.ELEMENT_NODE]
        if not children:lines[-1]+=' />';return lines
        lines[-1]+='>'
        for child in children:lines+=['']+render(child,depth+1)
        return lines+['',indent+'</'+node.tagName+'>']
    path=ROOT/name;path.parent.mkdir(parents=True,exist_ok=True)
    # Values resources contain text nodes and should retain their hand-written formatting.
    path.write_text('<?xml version="1.0" encoding="utf-8"?>\n'+'\n'.join(render(doc.documentElement))+'\n',encoding='utf-8')

write('layout/fragment_recipes.xml','''<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" xmlns:app="http://schemas.android.com/apk/res-auto" xmlns:tools="http://schemas.android.com/tools" android:layout_width="match_parent" android:layout_height="match_parent" android:orientation="vertical" android:paddingHorizontal="20dp" android:paddingTop="16dp" tools:context=".fragment.RecipesFragment">
<LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:gravity="center_vertical">
<LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:orientation="vertical">
<TextView android:id="@+id/eyebrow" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="MY RECIPE LAB • ẨM THỰC MỞ" android:textSize="10sp" android:letterSpacing="0.12" android:textColor="#A35431" android:textStyle="bold"/>
<TextView android:id="@+id/heading" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="6dp" android:text="Bếp Việt, mỗi ngày" android:fontFamily="serif" android:textSize="29sp" android:textColor="#123B2D"/>
</LinearLayout>
<com.google.android.material.button.MaterialButton android:id="@+id/ai_camera" android:layout_width="wrap_content" android:layout_height="48dp" android:text="AI ✦" android:textSize="13sp" android:contentDescription="Nhập ảnh món ăn bằng AI" app:cornerRadius="24dp"/>
</LinearLayout>
<com.google.android.material.textfield.TextInputLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="12dp" style="@style/Widget.Material3.TextInputLayout.OutlinedBox" app:boxCornerRadiusTopStart="16dp" app:boxCornerRadiusTopEnd="16dp" app:boxCornerRadiusBottomStart="16dp" app:boxCornerRadiusBottomEnd="16dp">
<com.google.android.material.textfield.TextInputEditText android:id="@+id/search" android:layout_width="match_parent" android:layout_height="48dp" android:paddingVertical="8dp" android:hint="Hôm nay bạn muốn nấu gì?" android:inputType="text" android:singleLine="true" android:textSize="14sp"/>
</com.google.android.material.textfield.TextInputLayout>
<HorizontalScrollView android:layout_width="match_parent" android:layout_height="44dp" android:layout_marginTop="8dp" android:scrollbars="none">
<com.google.android.material.chip.ChipGroup android:id="@+id/category_chips" android:layout_width="wrap_content" android:layout_height="wrap_content" app:singleLine="true" app:singleSelection="true" app:selectionRequired="true">
<com.google.android.material.chip.Chip android:id="@+id/chip_all" android:layout_width="wrap_content" android:layout_height="wrap_content" style="@style/Widget.Material3.Chip.Filter" android:text="Tất cả" android:checked="true"/>
<com.google.android.material.chip.Chip android:id="@+id/chip_soup" android:layout_width="wrap_content" android:layout_height="wrap_content" style="@style/Widget.Material3.Chip.Filter" android:text="Món nước"/>
<com.google.android.material.chip.Chip android:id="@+id/chip_main" android:layout_width="wrap_content" android:layout_height="wrap_content" style="@style/Widget.Material3.Chip.Filter" android:text="Món mặn"/>
<com.google.android.material.chip.Chip android:id="@+id/chip_vegan" android:layout_width="wrap_content" android:layout_height="wrap_content" style="@style/Widget.Material3.Chip.Filter" android:text="Ăn chay"/>
<com.google.android.material.chip.Chip android:id="@+id/chip_sweet" android:layout_width="wrap_content" android:layout_height="wrap_content" style="@style/Widget.Material3.Chip.Filter" android:text="Bánh &amp; chè"/>
</com.google.android.material.chip.ChipGroup></HorizontalScrollView>
<com.google.android.material.card.MaterialCardView android:id="@+id/hero_card" android:layout_width="match_parent" android:layout_height="138dp" android:layout_marginTop="8dp" app:cardCornerRadius="20dp" app:cardElevation="4dp" app:strokeWidth="0dp" android:clickable="true" android:focusable="true">
<FrameLayout android:layout_width="match_parent" android:layout_height="match_parent">
<ImageView android:id="@+id/hero_photo" android:layout_width="match_parent" android:layout_height="match_parent" android:scaleType="centerCrop" android:src="@drawable/food_art" android:contentDescription="Món ăn nổi bật trong thư viện"/>
<View android:layout_width="match_parent" android:layout_height="match_parent" android:background="@drawable/hero_scrim"/>
<LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_gravity="bottom" android:orientation="vertical" android:padding="16dp">
<TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="TỪ KHO CÔNG THỨC MỞ" android:textSize="9sp" android:textStyle="bold" android:letterSpacing="0.12" android:textColor="#F4CA78"/>
<TextView android:id="@+id/hero_title" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="Một món ngon, một câu chuyện" android:textSize="25sp" android:fontFamily="serif" android:textColor="#FFFFFF"/>
<TextView android:id="@+id/hero_meta" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="Khám phá công thức có nguồn →" android:textSize="11sp" android:textColor="#F8F5EC"/>
</LinearLayout></FrameLayout></com.google.android.material.card.MaterialCardView>
<LinearLayout android:layout_width="match_parent" android:layout_height="44dp" android:gravity="center_vertical" android:layout_marginTop="8dp">
<Spinner android:id="@+id/books" android:layout_width="0dp" android:layout_height="44dp" android:layout_weight="1" android:contentDescription="Bộ sưu tập"/>
<CheckBox android:id="@+id/favorites" android:layout_width="wrap_content" android:layout_height="44dp" android:text="♥" android:contentDescription="Chỉ món yêu thích"/>
<com.google.android.material.button.MaterialButton android:id="@+id/add" android:layout_width="wrap_content" android:layout_height="44dp" android:text="+ Món của tôi" style="@style/Widget.Material3.Button.TextButton" android:textSize="12sp"/>
</LinearLayout>
<TextView android:id="@+id/empty" android:layout_width="match_parent" android:layout_height="wrap_content" android:paddingBottom="4dp" android:text="Đang mở thư viện…" android:textSize="11sp" android:textColor="#617067"/>
<GridView android:numColumns="2" android:horizontalSpacing="10dp" android:verticalSpacing="10dp" android:stretchMode="columnWidth" android:id="@+id/list" android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1" android:clipToPadding="false" android:paddingBottom="12dp" tools:listitem="@layout/item_recipe"/>
</LinearLayout>''')

write('layout/item_recipe.xml','''<com.google.android.material.card.MaterialCardView xmlns:android="http://schemas.android.com/apk/res/android" xmlns:app="http://schemas.android.com/apk/res-auto" xmlns:tools="http://schemas.android.com/tools" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_margin="5dp" android:clickable="true" android:focusable="true" app:cardCornerRadius="16dp" app:cardElevation="2dp" app:strokeColor="#E7E4D9" app:strokeWidth="1dp" app:cardBackgroundColor="#FFFFFF">
<LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="vertical">
<ImageView android:id="@+id/photo" android:layout_width="match_parent" android:layout_height="114dp" android:scaleType="centerCrop" android:src="@drawable/food_art" android:contentDescription="Ảnh món ăn; xem tác giả và giấy phép trong chi tiết"/>
<TextView android:id="@+id/title" android:layout_width="match_parent" android:layout_height="wrap_content" android:minLines="2" android:maxLines="2" android:ellipsize="end" android:paddingHorizontal="12dp" android:paddingTop="10dp" android:textSize="16sp" android:textStyle="bold" android:textColor="#123B2D" tools:text="Bún bò Huế"/>
<TextView android:id="@+id/meta" android:layout_width="match_parent" android:layout_height="wrap_content" android:maxLines="2" android:ellipsize="end" android:paddingHorizontal="12dp" android:paddingTop="5dp" android:paddingBottom="12dp" android:textSize="10sp" android:textColor="#687469" tools:text="Wikibooks • CC BY-SA 4.0"/>
</LinearLayout></com.google.android.material.card.MaterialCardView>''')

write('drawable/hero_scrim.xml','''<shape xmlns:android="http://schemas.android.com/apk/res/android"><gradient android:angle="90" android:startColor="#E8123B2D" android:endColor="#00123B2D"/></shape>''')

write('layout/fragment_import.xml','''<ScrollView xmlns:android="http://schemas.android.com/apk/res/android" xmlns:app="http://schemas.android.com/apk/res-auto" xmlns:tools="http://schemas.android.com/tools" android:layout_width="match_parent" android:layout_height="match_parent" android:fillViewport="true" tools:context=".fragment.ImportFragment">
<LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="vertical" android:padding="24dp">
<TextView android:layout_width="match_parent" android:layout_height="wrap_content" android:text="TỪ CẢM HỨNG ĐẾN CĂN BẾP" android:textSize="10sp" android:letterSpacing="0.12" android:textStyle="bold" android:textColor="#A35431"/>
<LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:gravity="center_vertical">
<TextView android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:text="Mang món ngon về" android:fontFamily="serif" android:textSize="30sp" android:textColor="#123B2D"/>
<com.google.android.material.button.MaterialButton android:id="@+id/ai_settings" android:layout_width="wrap_content" android:layout_height="48dp" android:text="Kết nối AI" android:textSize="11sp" style="@style/Widget.Material3.Button.TextButton"/>
</LinearLayout>
<com.google.android.material.card.MaterialCardView android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="16dp" app:cardCornerRadius="20dp" app:cardBackgroundColor="#E8EEE5" app:strokeWidth="0dp">
<LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:padding="18dp" android:orientation="vertical">
<TextView android:layout_width="match_parent" android:layout_height="wrap_content" android:text="✦ Nhìn món ăn, tìm công thức" android:textSize="20sp" android:fontFamily="serif" android:textColor="#123B2D"/>
<TextView android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="6dp" android:text="AI trên laptop gợi ý món tương tự. Bạn chọn món rồi xem công thức có nguồn trong thư viện." android:textSize="13sp" android:textColor="#526455"/>
<com.google.android.material.button.MaterialButton android:id="@+id/ai_pick" android:layout_width="match_parent" android:layout_height="52dp" android:layout_marginTop="10dp" android:text="Chọn ảnh món ăn" app:cornerRadius="14dp"/>
</LinearLayout></com.google.android.material.card.MaterialCardView>
<LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="12dp">
<com.google.android.material.button.MaterialButton android:id="@+id/pick" android:layout_width="0dp" android:layout_height="48dp" android:layout_weight="1" android:text="Ảnh công thức" android:textSize="12sp" style="@style/Widget.Material3.Button.OutlinedButton" app:cornerRadius="14dp"/>
<com.google.android.material.button.MaterialButton android:id="@+id/camera" android:layout_width="0dp" android:layout_height="48dp" android:layout_weight="1" android:layout_marginStart="8dp" android:text="Chụp ảnh" android:textSize="12sp" style="@style/Widget.Material3.Button.OutlinedButton" app:cornerRadius="14dp"/>
</LinearLayout>
<com.google.android.material.textfield.TextInputLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="14dp" style="@style/Widget.Material3.TextInputLayout.OutlinedBox">
<com.google.android.material.textfield.TextInputEditText android:id="@+id/url" android:layout_width="match_parent" android:layout_height="52dp" android:hint="Link TikTok hoặc trang công thức" android:inputType="textUri" android:singleLine="true" android:textSize="13sp"/>
</com.google.android.material.textfield.TextInputLayout>
<com.google.android.material.button.MaterialButton android:id="@+id/fetch" android:layout_width="match_parent" android:layout_height="48dp" android:text="Lấy nội dung từ link" app:cornerRadius="14dp"/>
<ProgressBar android:id="@+id/progress" android:layout_width="32dp" android:layout_height="32dp" android:layout_gravity="center" android:visibility="gone"/>
<TextView android:id="@+id/status" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginVertical="12dp" android:text="Ảnh có chữ dùng OCR trên điện thoại. TikTok đọc caption công khai khi nền tảng cho phép. Luôn kiểm tra kết quả trước khi lưu." android:textSize="12sp" android:textColor="#647265"/>
<EditText android:id="@+id/raw" android:layout_width="match_parent" android:layout_height="wrap_content" android:minLines="5" android:gravity="top" android:hint="Dán caption, ghi chú hoặc sửa chữ vừa nhận từ ảnh…" android:inputType="textMultiLine|textCapSentences" android:textSize="14sp"/>
<com.google.android.material.button.MaterialButton android:id="@+id/ai_text" android:layout_width="match_parent" android:layout_height="48dp" android:text="✦ Tìm món từ caption / nguyên liệu" style="@style/Widget.Material3.Button.OutlinedButton" app:cornerRadius="14dp"/>
<com.google.android.material.button.MaterialButton android:id="@+id/review" android:layout_width="match_parent" android:layout_height="52dp" android:text="Tách công thức → kiểm tra &amp; lưu" app:cornerRadius="14dp"/>
<com.google.android.material.button.MaterialButton android:id="@+id/manual" android:layout_width="match_parent" android:layout_height="48dp" android:text="Hoặc viết công thức của bạn" style="@style/Widget.Material3.Button.TextButton"/>
</LinearLayout></ScrollView>''')

write('layout/fragment_ai_settings.xml','''<ScrollView xmlns:android="http://schemas.android.com/apk/res/android" xmlns:app="http://schemas.android.com/apk/res-auto" xmlns:tools="http://schemas.android.com/tools" android:layout_width="match_parent" android:layout_height="match_parent" tools:context=".fragment.AiSettingsFragment">
<LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="vertical" android:padding="24dp">
<Button android:id="@+id/back" android:layout_width="wrap_content" android:layout_height="48dp" android:text="‹ Quay lại" style="@style/Widget.Material3.Button.TextButton"/>
<TextView android:layout_width="match_parent" android:layout_height="wrap_content" android:text="Căn bếp có AI" android:fontFamily="serif" android:textSize="32sp" android:textColor="#123B2D"/>
<TextView android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginVertical="16dp" android:text="Kết nối laptop của bạn để nhận diện món và tìm công thức. Không cần tài khoản hay phí API. Ảnh chỉ được gửi tới địa chỉ bạn cấu hình khi chọn phân tích AI." android:textSize="15sp"/>
<EditText android:id="@+id/ai_endpoint" android:layout_width="match_parent" android:layout_height="56dp" android:hint="Địa chỉ laptop AI" android:inputType="textUri" android:singleLine="true"/>
<com.google.android.material.button.MaterialButton android:id="@+id/ai_check" android:layout_width="match_parent" android:layout_height="52dp" android:text="Lưu &amp; kiểm tra kết nối" app:cornerRadius="14dp"/>
<TextView android:id="@+id/ai_health" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginVertical="16dp" android:text="Chưa kiểm tra kết nối." android:textSize="15sp" android:textIsSelectable="true"/>
<TextView android:layout_width="match_parent" android:layout_height="wrap_content" android:text="Nếu chưa chạy server, xem README trong thư mục backend. Kết nối USB có thể dùng ADB reverse; dùng Wi-Fi cần địa chỉ LAN và cùng mạng.\n\nAI chỉ gợi ý món từ ảnh. Không thể biết chắc định lượng, chất gây dị ứng hay toàn bộ cách nấu từ một bức ảnh. Hãy kiểm tra công thức nguồn trước khi dùng." android:textSize="13sp" android:lineSpacingExtra="4dp"/>
</LinearLayout></ScrollView>''')

print('Native XML redesigned and expanded')

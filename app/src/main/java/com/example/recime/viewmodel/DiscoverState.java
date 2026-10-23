package com.example.recime.viewmodel;
import android.app.Application;
import androidx.lifecycle.*;
import com.example.recime.AIservice.AccountClient;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public final class DiscoverState extends AndroidViewModel {
  public final MutableLiveData<String> status = new MutableLiveData<>("Catalog backend riêng · thư viện offline luôn có ở tab Discover");
  public final MutableLiveData<List<JSONObject>> results = new MutableLiveData<>(new ArrayList<>());
  public final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
  private final ExecutorService io = Executors.newSingleThreadExecutor();
  public String query = "";
  public int nextOffset = 0;
  public DiscoverState(Application application) { super(application); }
  public void search(String input, boolean next) {
    if (Boolean.TRUE.equals(loading.getValue()) || next && nextOffset < 0) return;
    if (!next) { query = input.trim(); nextOffset = 0; results.setValue(new ArrayList<>()); }
    int offset = nextOffset; String search = query;
    loading.setValue(true); status.setValue("Đang tải catalog…");
    io.execute(() -> {
      try {
        String url = "/v1/discover?limit=30&offset=" + offset + "&q=" + java.net.URLEncoder.encode(search, "UTF-8");
        JSONObject data = new AccountClient(getApplication()).request("GET", url, null);
        List<JSONObject> rows = new ArrayList<>(results.getValue());
        JSONArray items = data.getJSONArray("recipes");
        for (int i = 0; i < items.length(); i++) rows.add(items.getJSONObject(i));
        nextOffset = data.isNull("nextOffset") ? -1 : data.getInt("nextOffset");
        results.postValue(rows);
        status.postValue(rows.isEmpty() ? "Chưa có kết quả phù hợp" : rows.size() + " / " + data.getInt("total") + " công thức có nguồn");
      } catch (Exception error) { status.postValue("Chưa tải được: " + error.getMessage() + ". Thử lại hoặc Back để dùng catalog offline."); }
      finally { loading.postValue(false); }
    });
  }
  @Override protected void onCleared() { io.shutdown(); }
}

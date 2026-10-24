package com.example.recime.helper;

import org.json.*;

/** Preserve uncertainty, evidence and suggestions in editable review text. */
public final class CookingRecipeReview {
  public static String render(JSONObject result) throws JSONException {
    JSONObject recipe = result.getJSONObject("recipe");
    if (!recipe.getBoolean("is_cooking")) throw new JSONException("Nội dung không phải nấu ăn; chưa có công thức để lưu");
    StringBuilder text = new StringBuilder(recipe.isNull("title") ? "Món chưa xác định" : recipe.getString("title").replace('\n', ' '));
    text.append("\nIngredients\n");
    JSONArray ingredients = recipe.getJSONArray("ingredients");
    for (int n = 0; n < ingredients.length(); n++) {
      JSONObject item = ingredients.getJSONObject(n);
      if (!item.isNull("quantity")) text.append(item.get("quantity")).append(' ').append(item.optString("unit", "")).append(' ');
      text.append(item.getString("name").replace('\n', ' '));
      if (item.isNull("quantity")) text.append(" (chưa rõ lượng)");
      text.append('\n');
    }
    text.append("Instructions\n");
    JSONArray steps = recipe.getJSONArray("steps");
    for (int n = 0; n < steps.length(); n++) {
      JSONObject item = steps.getJSONObject(n);
      text.append(item.getString("text").replace('\n', ' ')).append('\n');
    }
    StringBuilder notes = new StringBuilder("reCime AI: ").append(result.getString("provider")).append(" / ").append(result.getString("modelVersion"));
    notes.append(" · Phạm vi: ").append(result.optString("scope"));
    for (String field : new String[] {"ingredients", "steps"}) {
      JSONArray items = recipe.getJSONArray(field);
      for (int n = 0; n < items.length(); n++) {
        JSONObject evidence = items.getJSONObject(n).getJSONObject("evidence");
        notes.append(" · Bằng chứng ").append(evidence.getString("source"));
        if (!evidence.isNull("start")) notes.append(" @ ").append(evidence.get("start")).append("–").append(evidence.get("end")).append("s");
        notes.append(": ").append(evidence.getString("text").replace('\n', ' '));
      }
    }
    for (String field : new String[] {"uncertainty"}) {
      JSONArray lines = recipe.getJSONArray(field);
      for (int n = 0; n < lines.length(); n++) notes.append(" · Chưa chắc: ").append(lines.getString(n).replace('\n', ' '));
    }
    JSONArray warnings = result.optJSONArray("warnings");
    if (warnings != null) for (int n = 0; n < warnings.length(); n++) notes.append(" · ").append(warnings.getString(n).replace('\n', ' '));
    if (!recipe.isNull("suggested_recipe")) notes.append(" · GỢI Ý GIẢ ĐỊNH, cần tự kiểm tra: ").append(recipe.getString("suggested_recipe").replace('\n', ' '));
    text.append("Attribution: ").append(notes);
    return text.toString();
  }
  private CookingRecipeReview() {}
}

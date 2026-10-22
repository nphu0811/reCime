package com.example.recime;

import static org.junit.Assert.*;

import android.graphics.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.recime.helper.PublicWebExtractor;
import com.example.recime.helper.RecipeParser;
import com.example.recime.model.ParsedRecipe;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ImportIntegrationTest {
  @Test
  public void bundledOcrReadsSelfCreatedRecipeImageThenParserReviews() throws Exception {
    Bitmap bitmap = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    canvas.drawColor(Color.WHITE);
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setColor(Color.BLACK);
    paint.setTextSize(52);
    String[] lines = {
      "Tofu bowl",
      "Ingredients",
      "200 g tofu",
      "100 ml water",
      "Instructions",
      "Cook tofu in water."
    };
    for (int i = 0; i < lines.length; i++) canvas.drawText(lines[i], 50, 100 + i * 100, paint);
    TextRecognizer reader = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    try {
      Text result =
          Tasks.await(reader.process(InputImage.fromBitmap(bitmap, 0)), 45, TimeUnit.SECONDS);
      assertTrue(result.getText(), result.getText().contains("tofu"));
      ParsedRecipe parsed = RecipeParser.parse(result.getText());
      assertTrue(parsed.ingredients, parsed.ingredients.contains("200"));
      assertFalse(parsed.steps.isEmpty());
    } finally {
      reader.close();
      bitmap.recycle();
    }
  }

  @Test
  public void webAdapterRefusesPrivateAndSocialInputs() throws Exception {
    for (String url :
        new String[] {
          "http://example.com/recipe",
          "https://127.0.0.1/recipe",
          "https://[::1]/recipe",
          "https://192.168.1.1/",
          "https://www.tiktok.com/video/1",
          "https://user:pass@example.com/"
        }) {
      try {
        PublicWebExtractor.validate(url);
        fail("Must reject " + url);
      } catch (IllegalArgumentException | java.net.UnknownHostException expected) {
      }
    }
  }
}

import io,json,unittest
from pathlib import Path
from unittest.mock import patch
from fastapi.testclient import TestClient
from PIL import Image
from backend.app import app,tiktok_caption
from backend.engine import RecipeEngine,CATALOG

class RecipeBackendTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):cls.client=TestClient(app);cls.engine=RecipeEngine()

    def test_catalog_has_real_licensed_content_and_offline_photos(self):
        self.assertGreaterEqual(len(self.engine.recipes),547)
        for r in self.engine.recipes:
            self.assertTrue(r['ingredients']);self.assertTrue(r['steps']);self.assertTrue(r['source'].startswith('https://'))
            self.assertIn('CC BY-SA',r['license']);self.assertTrue(r['history']);self.assertIsInstance(r['servingsKnown'],bool)
            if r.get('image'):
                self.assertTrue((CATALOG/r['image']).is_file());self.assertTrue(r['imageAttribution']);self.assertTrue(r['imageLicense']);self.assertTrue(r['imageSource'].startswith('https://commons.wikimedia.org/'))

    def test_recipe_model_finds_named_recipe_and_refuses_unrelated_text(self):
        matches=self.engine.text('bún bò Huế')
        self.assertEqual('Bún bò Huế',matches[0]['title'])
        self.assertEqual([],self.engine.text('quasar xylophone zxqv'))
        for r in matches:self.assertTrue(r['requiresConfirmation']);self.assertIn(r['catalogKey'],self.engine.by_key)

    def test_health_and_text_api_return_sources(self):
        response=self.client.get('/health');self.assertEqual(200,response.status_code);self.assertGreaterEqual(response.json()['recipes'],547)
        response=self.client.post('/analyze/text',json={'text':'bánh xèo'});self.assertEqual(200,response.status_code);self.assertTrue(response.json()['candidates'][0]['source'])

    def test_invalid_image_is_recoverable(self):
        response=self.client.post('/analyze/image',files={'photo':('bad.jpg',b'not an image','image/jpeg')})
        self.assertEqual(422,response.status_code)

    def test_tiktok_only_allows_official_https_hosts(self):
        for url in ['https://tiktok.com.evil.test/video/1','http://www.tiktok.com/video/1','https://127.0.0.1/video/1','https://user:pass@www.tiktok.com/video/1']:
            with self.assertRaises(ValueError):tiktok_caption(url)

    def test_tiktok_caption_uses_oembed_and_does_not_download_video(self):
        class Reply:
            status_code=200
            def json(self):return {'title':'Bún bò Huế ngon tại nhà','author_name':'Test creator'}
        with patch('backend.app.requests.get',return_value=Reply()) as request:
            result=tiktok_caption('https://www.tiktok.com/@test/video/123456789')
            self.assertEqual('Bún bò Huế ngon tại nhà',result['caption']);self.assertEqual(1,request.call_count)
            self.assertEqual('https://www.tiktok.com/oembed',request.call_args.args[0])

    def test_real_onnx_image_model_roundtrip_with_reference(self):
        recipe=next(r for r in self.engine.recipes if r['title']=='Bún bò Huế' and r.get('image'))
        result=self.engine.image((CATALOG/recipe['image']).read_bytes())
        self.assertTrue(any(r['catalogKey']==recipe['catalogKey'] for r in result['candidates']))
        self.assertGreaterEqual(result['referenceImages'],80)
        # Only an in-gallery roundtrip, deliberately not reported as unseen-image accuracy.

if __name__=='__main__':unittest.main()

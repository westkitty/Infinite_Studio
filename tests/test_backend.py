"""No network calls or API charges; fake provider responses are injected."""
import base64
import importlib.util
import io
import json
import pathlib
import threading
import urllib.error
import urllib.request
import unittest
from PIL import Image

MODULE_PATH = pathlib.Path(__file__).parents[1] / 'server.py'
spec=importlib.util.spec_from_file_location('studio_server_test',str(MODULE_PATH))
module=importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def png(alpha=True, width=8, height=8):
    im=Image.new('RGBA' if alpha else 'RGB',(width,height),(255,0,0,255))
    stream=io.BytesIO();im.save(stream,format='PNG');return stream.getvalue()

def data(b):return 'data:image/png;base64,'+base64.b64encode(b).decode('ascii')


class BackendTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.http=module.ThreadingHTTPServer(('127.0.0.1',0),module.Handler)
        cls.thread=threading.Thread(target=cls.http.serve_forever,daemon=True)
        cls.thread.start()
        cls.base='http://127.0.0.1:'+str(cls.http.server_address[1])
    @classmethod
    def tearDownClass(cls):cls.http.shutdown();cls.http.server_close()
    def setUp(self):
        self.allow, self.key,self.max_calls=module.ALLOW,module.KEY,module.MAX_REQUESTS
        self.urlopen=module.urllib.request.urlopen
        module.ALLOW=False;module.KEY='';module.MAX_REQUESTS=3
        module.COUNTER['calls']=0
    def tearDown(self):
        module.ALLOW=self.allow;module.KEY=self.key;module.MAX_REQUESTS=self.max_calls
        module.urllib.request.urlopen=self.urlopen
    def request(self, body):
        req=urllib.request.Request(self.base+'/api/edits',data=json.dumps(body).encode(),headers={'Content-Type':'application/json'},method='POST')
        try:
            with self.urlopen(req) as r:return r.status,json.load(r)
        except urllib.error.HTTPError as e:return e.code,json.load(e)
    def test_disabled_by_default(self):
        with self.urlopen(self.base+'/api/health') as res: self.assertFalse(json.load(res)['can_generate'])
        status,body=self.request({'model':'gpt-image-2.6-flare','prompt':'change','image':data(png())})
        self.assertEqual(status,403);self.assertIn('disabled',body['error'])
    def test_mask_png_and_dimension_validation(self):
        raw,details=module.decode_data_url(data(png()),mask=True)
        self.assertEqual(details[:2],(8,8))
        with self.assertRaises(ValueError):module.decode_data_url(data(png(alpha=False)),mask=True)
        with self.assertRaises(ValueError):module.decode_data_url('data:image/png;base64,not-base64!!!')
        with self.assertRaises(ValueError):module.decode_data_url(data(png(width=2200)))
    def test_fake_upstream_complete_call_path_and_cap(self):
        module.ALLOW=True;module.KEY='test-only-key';module.MAX_REQUESTS=1
        captured={}
        test_data=data(png())
        class FakeResult:
            def __enter__(self):return self
            def __exit__(self,*args):pass
            def read(self,*args):return json.dumps({'data':[{'b64_json':test_data.partition(',')[2]}]}).encode()
        def fake_open(req,timeout=None):
            captured['url']=req.full_url
            captured['auth']=req.headers.get('Authorization')
            captured['bytes']=req.data
            return FakeResult()
        module.urllib.request.urlopen=fake_open
        req_body={'model':'gpt-image-2.6-flare','prompt':'Turn this mark blue','image':data(png()),'mask':data(png())}
        status,res=self.request(req_body)
        self.assertEqual(status,200)
        self.assertEqual(res['image'],test_data)
        self.assertEqual(captured['url'],module.OPENAI_URL)
        self.assertEqual(captured['auth'],'Bearer test-only-key')
        self.assertIn(b'name="image[]"',captured['bytes'])
        self.assertIn(b'name="mask"',captured['bytes'])
        self.assertIn(b'gpt-image-2.6-flare',captured['bytes'])
        # Prove a second call is blocked after the first reservation.
        status,_=self.request(req_body)
        self.assertEqual(status,429)
    def test_mask_dimensions_must_match_before_any_charge(self):
        module.ALLOW=True;module.KEY='test-only-key'
        status,body=self.request({'model':'gpt-image-2.6-sunburst','prompt':'Paint','image':data(png()),'mask':data(png(width=7))})
        self.assertEqual(status,400)
        self.assertEqual(module.COUNTER['calls'],0)
    def test_wrong_origin_rejected(self):
        module.ALLOW=True;module.KEY='test-only-key'
        req=urllib.request.Request(self.base+'/api/edits',data=b'{}',method='POST',headers={'Content-Type':'application/json','Origin':'https://malicious.example'})
        with self.assertRaises(urllib.error.HTTPError) as ctx:self.urlopen(req)
        self.assertEqual(ctx.exception.code,403)

if __name__=='__main__':unittest.main()

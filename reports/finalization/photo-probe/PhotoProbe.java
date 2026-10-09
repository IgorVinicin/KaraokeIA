import android.content.ContextWrapper;
import android.graphics.*;
import java.io.*;
import com.araujo.karaoke_ia.remote.ScorePhotoExport;
public class PhotoProbe {
 public static void main(String[] args) throws Exception {
  java.lang.reflect.Method fontInit = Typeface.class.getDeclaredMethod("loadPreinstalledSystemFontMap"); fontInit.setAccessible(true); fontInit.invoke(null); final File root = new File("/data/local/tmp/karaoke-photo-qa"); root.mkdirs();
  ContextWrapper ctx = new ContextWrapper(null) { public File getFilesDir() { return root; } };
  Bitmap input = Bitmap.createBitmap(2400, 1800, Bitmap.Config.ARGB_8888);
  Canvas canvas = new Canvas(input); canvas.drawColor(Color.rgb(80,140,190));
  Paint p = new Paint(); p.setColor(Color.WHITE); p.setTextSize(150); canvas.drawText("FOTO TESTE", 650,900,p);
  File source = new File(root,"source.jpg"); try(FileOutputStream out=new FileOutputStream(source)){input.compress(Bitmap.CompressFormat.JPEG,90,out);} input.recycle();
  String token="12345678-1234-1234-1234-123456789abc";
  File export = ScorePhotoExport.INSTANCE.create(ctx,source,token,98,"Minha musica de teste",1);
  Bitmap result = BitmapFactory.decodeFile(export.getPath());
  if(result.getWidth()!=1080 || result.getHeight()!=1350) throw new AssertionError("Dimensions");
  if(result.getPixel(5,5)==result.getPixel(1070,1340)) throw new AssertionError("Frame gradient");
  if(ScorePhotoExport.INSTANCE.create(ctx,source,token,98,"teste",1).length()!=export.length()) throw new AssertionError("Cache");
  try { ScorePhotoExport.INSTANCE.create(ctx,source,"../invalid",98,"test",1); throw new AssertionError("Traversal accepted"); } catch(IllegalArgumentException expected) {}
  System.out.println("PASS: 1080x1350 JPEG, colored frame, large-source downsampling, cached export, traversal rejected; bytes="+export.length()); result.recycle();
 }
}


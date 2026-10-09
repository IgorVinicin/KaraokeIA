import com.araujo.karaoke_ia.online.OnlineRepository;
public class OnlineProbe {
 public static void main(String[] args) {
  String id="M7lc1UVf-VE";
  String[] valid={id,"https://www.youtube.com/watch?v="+id+"&list=123","https://youtu.be/"+id+"?t=1","youtube.com/shorts/"+id,"https://m.youtube.com/live/"+id,"https://www.youtube.com/embed/"+id};
  for(String s:valid) if(!id.equals(OnlineRepository.Companion.parseVideoId(s))) throw new AssertionError("valid="+s);
  String[] invalid={"https://evil.example/watch?v="+id,"https://youtube.com.evil.example/watch?v="+id,"javascript:alert(1)","https://youtube.com/watch?v=short","https://youtube.com/playlist?list="+id,"","../video"};
  for(String s:invalid) if(OnlineRepository.Companion.parseVideoId(s)!=null) throw new AssertionError("invalid="+s);
  System.out.println("PASS: 6 YouTube URL/ID formats accepted, 7 malformed or unrelated URLs rejected");
 }
}

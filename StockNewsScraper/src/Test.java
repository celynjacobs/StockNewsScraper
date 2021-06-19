import java.io.File;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Scanner;

public class Test {
	public static void main(String[] args) {
		StringBuffer buffer = null;
		String website = "www.viomi.com.cn";
		try {
			 URL url = new URL("https://" + "www.viomi.com.cn");
			 InputStream is = url.openStream();
				int ptr = 0;
				buffer = new StringBuffer();
				while ((ptr = is.read()) != -1) {
				    buffer.append((char)ptr);
				}
				is.close();
			} catch (MalformedURLException e) {
				System.out.println("Problem reaching website: " + website);
			} catch (Exception ex) {
				System.out.println(ex.getMessage());
				ex.printStackTrace();
			}
			System.out.println(buffer.toString());
	}
}

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

public class YahooFinanceScraper {
	private static final String IN_FILE_NAME = "NASDAQ.txt";
	private static final String OUT_FILE_NAME = "StockWebsites.txt";
	private static Set<String> symbols = new HashSet<String> ();
	private static Map<String,String> websiteMap = new HashMap<String,String> ();
	private static Set<String> symsToRetry = new HashSet<String> ();
	
	public static void main(String[] args) {
		getSymbols();
		symbols.parallelStream().forEach(symbol -> {
			String site = findWebsite(symbol);
			System.out.println("Website for " + symbol + ": " + site);
			websiteMap.put(symbol,site);
		});
		symsToRetry.parallelStream().forEach(symbol -> {
			String site = findWebsite(symbol);
			System.out.println("Website for " + symbol + ": " + site);
			websiteMap.put(symbol,site);
		});
		writeWebsites();
	}
	
	private static void getSymbols() {
		try {
			Scanner fileScanner = new Scanner(new File(IN_FILE_NAME));
			fileScanner.nextLine(); //skip first line that just has labels
			while(fileScanner.hasNextLine()) {
				String company = fileScanner.nextLine();
				String companySymbol = company.split("\t")[0];
				symbols.add(companySymbol);
			}
		}catch (FileNotFoundException e) {
			System.out.println("Input file not found!");
		}
	}
	public static String findWebsite(String symbol) {
		String html = fetchHTML(symbol);
		String website = scrapeHTML(html, symbol);
		return website;
	}
	
	/**
	 * This method takes a stock symbol as input. It then gets the
	 * Yahoo! finance profile page link for that stock symbol. It then navigates to the page
	 * and collects the HTML from the page in a string buffer. It finally converts that
	 * HTML to a string and returns it.
	 */
	private static String fetchHTML(String symbol) {
		StringBuffer buffer = null;
		try {
			URL url = new URL(getURL(symbol));
			InputStream is = url.openStream();
			int ptr = 0;
			buffer = new StringBuffer();
			while ((ptr = is.read()) != -1) {
			    buffer.append((char)ptr);
			}
			is.close();
		} catch (MalformedURLException e) {
			System.out.println("Yahoo! Finance page for " + symbol + " DNE!");
			return null;
		} catch (Exception ex) {
			System.out.println(ex.getMessage());
			ex.printStackTrace();
			symsToRetry.add(symbol);
		}
		if(buffer == null)
			return null;
		else
			return buffer.toString();
	}
	
	/**
	 * This method simply takes a stock symbol given as a parameter and returns
	 * the Yahoo! Finance profile page for that symbol.
	 */
	private static String getURL(String symbol) {
		return "https://finance.yahoo.com/quote/" + symbol + "/profile?p=" + symbol;
	}
	
	/**
	 * This method scrapes the website link from the html passed to this method
	 * from a Yahoo! Finance profile page and returns it. If no website is found, it returns
	 * "WebsiteNotFound".
	 * @param html
	 * @return
	 */
	private static String scrapeHTML(String html, String url) {
		String searchStr = "<a href=\"http://";
		String website = getWebsite(html,url,searchStr);
		if(website == null) {
			website = "WebsiteNotFound";
			System.out.println("Website not found for " + url);
		}
		return website;
		
	}
	
	private static String getWebsite(String html, String url, String searchStr) {
		if(html == null)
			return null;
		int patternLength = searchStr.length();
		int beginIndex = html.indexOf(searchStr);
		String retstr;
		
		if(beginIndex != -1) 
			retstr = checkedSubstr(html,beginIndex+patternLength);
		else 
			retstr = null;
		
		return retstr;
	}
	
	/**
	 * This method is a modified version of substr(). It takes a string as input and
	 * appends characters until it reaches a '"' character, at which point it returns the string it
	 * has created to that point. It will also stop appending if it finds a "#" or ":"
	 * character, which represent a bad page name, at which point the method will
	 * return null.
	 * @param str
	 * @param start
	 * @return
	 */
	private static String checkedSubstr(String str, int start) {
		StringBuilder retstr = new StringBuilder();
		for(int i = start; true ; ++i) {
			char c = str.charAt(i);
			if (c == '"')
				break;
			if (c == '#' || c == ':')
				return null;
			retstr.append(c);
		}
		return retstr.toString();
	}
	
	private static void writeWebsites() {
		try {
			FileWriter writer = new FileWriter(OUT_FILE_NAME);
			for(String symbol : symbols) {
				writer.write(symbol + "\t\t" + websiteMap.get(symbol) + "\n");
			}
			writer.close();
		}catch (FileNotFoundException e) {
			System.out.println("Input file not found!");
		}catch (IOException e) {
			System.out.println("Writing Error!");
			e.printStackTrace();
		}
	}
}

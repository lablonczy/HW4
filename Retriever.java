import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class Retriever {
	
	protected Networker networker;
	private boolean doDebug = false;
	protected static final String USER = "lukacsa", PASS = "Crashcourse1*";

	public abstract LinkedList<Assignment> retrieve() throws InterruptedException, IOException, URISyntaxException;

	public Retriever(){
		networker = new Networker();
	}

	public Networker getNetworker() {
		return networker;
	}
	
	protected static String extractTag(String toParse, String toMatch, String tagName){
		long time = System.currentTimeMillis();

		//read until <tagName is found, record until > is found, take index + 1.
		char[] htmlChars = toParse.toCharArray();
		String comparator = toMatch;
		StringBuilder builder = new StringBuilder();
		int size = htmlChars.length;

		int firstStop = -1;
		for(int i=0;i<size;i++){
			if(htmlChars[i] == '<') {
				if(htmlChars[i+1] == '/')
					continue;
				for(int j = 1; j < comparator.length() + 1; j++)
					 builder.append(htmlChars[i+j]);
				if(builder.toString().equals(comparator)) {
					firstStop = i + comparator.length() + 1;
					if(htmlChars[firstStop] != '>') {
						builder.setLength(0);
						continue;
					}
					break;
				}

				builder.setLength(0);
			}
		}

		if(firstStop == -1)
			return "FAIL FAIL";

		int secondStop = -1;
		for(int i=firstStop;i<size;i++) {
			if(htmlChars[i] == '>') {
				secondStop = i + 1;
				break;
			}
		}

		builder.setLength(0);
		for(int i=secondStop;i< htmlChars.length;i++) {
			if (htmlChars[i] == '<' && htmlChars[i + 1] == '/') {
				char[] test = tagName.toCharArray();
				for (int j = 0; j < test.length; j++) {
					if (htmlChars[i + j + 2] != test[j])
						break;
					if(j == test.length - 1 && htmlChars[i+j+2] == test[j]) {
						time = System.currentTimeMillis() - time;
						return builder.toString();
					}
				}
			}
			builder.append(htmlChars[i]);
		}

		time = System.currentTimeMillis() - time;
		return "FAIL FAIL";

	}

	protected LinkedList<String> extractValues(String toParse, String comparator, final String REGEX, String name){
		long time = System.currentTimeMillis();

		LinkedList<String> values = new LinkedList<>();
		char[] htmlAsArray = toParse.toCharArray();
		boolean quoteFound = false;
		String find = "", lastFound = "";
		StringBuilder builder = new StringBuilder();

		for(char currentChar : htmlAsArray){

			if(currentChar == '"') {
				if(quoteFound){
					find = builder.toString();
					if(lastFound.equals(comparator))
						values.add(find);

					lastFound = find;
					builder.setLength(0);
				}

				quoteFound = !quoteFound;
			} else if(quoteFound)
				builder.append(currentChar);
		}

		if(find.equals("")){
			doDebug(name + " resorted to regex");
			return fetchValues(toParse, REGEX);
		}

		time = System.currentTimeMillis() - time;
		return values;
	}
	protected String extractValue(String toParse, String comparator, final String REGEX, String name){
		long time = System.currentTimeMillis();

		char[] htmlAsArray = toParse.toCharArray();
		boolean quoteFound = false;
		String find = "", lastFound = "";
		StringBuilder builder = new StringBuilder();

		for(char currentChar : htmlAsArray){

			if(currentChar == '"') {
				if(quoteFound){
					find = builder.toString();
					if(lastFound.equals(comparator)) {
						time = System.currentTimeMillis() - time;
						return find;
					}

					lastFound = find;
					builder.setLength(0);
				}

				quoteFound = !quoteFound;
			} else if(quoteFound)
				builder.append(currentChar);
		}

		if(find.equals("")){
			doDebug(name + " resorted to regex");
			time = System.currentTimeMillis() - time;
			return fetchValue(toParse, REGEX);
		}

		time = System.currentTimeMillis() - time;
		return null;
	}


	//helper functions
	protected void doDebug(String toPrint){
		if(doDebug)
			System.out.println(toPrint);
	}

	protected static String fetchValue(String textToParse, String regex){
		Pattern pattern = Pattern.compile(regex);

		Matcher matcher = pattern.matcher(textToParse);
		if(matcher.find())
			return matcher.group();
		else
			return null;
	}

	protected static LinkedList<String> fetchValues(String html, String pat){
		LinkedList<String> matches = new LinkedList<>();

		Pattern pattern = Pattern.compile(pat);

		Matcher matcher = pattern.matcher(html);
		while(matcher.find())
			matches.add(matcher.group());

		return matches;
	}

	protected static boolean allContained(String comparator, String... matchContains){
		for(String s : matchContains)
			if(!comparator.contains(s))
				return false;

		return true;
	}
}

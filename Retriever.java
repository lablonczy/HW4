import java.io.IOException;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class Retriever {
	
	private boolean doDebug = false;
	protected static final String USER = "lukacsa", PASS = "Crashcourse1*";

	public abstract LinkedList<Assignment> retrieve() throws InterruptedException, IOException, URISyntaxException;

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
}

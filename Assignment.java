import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

public class Assignment implements Comparable<Assignment> {

	private String name, due, className;
	private long dueNum;
	private static final String[] WEEKDAYS = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
	private static final String[] MONTHS = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};
	private boolean submitted;
	private Instant dueInstant;

	public Assignment(String name) {
		setName(name);
		setSubmitted(false);
	}

	public Assignment(String name, String due) {
		setName(name);
		setDue(due);
		setSubmitted(false);
	}

	public Assignment(String name, String due, boolean submitted) {
		setName(name);
		setDue(due);
		setSubmitted(submitted);
	}

	public boolean isSubmitted() {
		return submitted;
	}

	public String getClassName() {
		return className;
	}

	public void setClassName(String className) {
		this.className = className;
	}

	public void setSubmitted(boolean submitted) {
		this.submitted = submitted;
	}

	@Override
	public String toString() {
		return (submitted?"!! ":"") + due + " | " + name + " | " + className;
	}

	public String getName() {
		return name;
	}

	private String fixStupidUnicodeOrWhatever(String string){
		StringBuilder builder = new StringBuilder();
		char[] chars = string.toCharArray();

		for(int i=0;i<chars.length;i++) {
			char current = chars[i];
			builder.append(current);

			if(current == '&')
				i+=4;
		}

		return builder.toString();
	}

	public void setName(String name) {
		int ampindex = name.indexOf('&');
		if(ampindex != -1)
			name = fixStupidUnicodeOrWhatever(name);

		this.name = name;
	}

	public String getDue() {
		return due;
	}

	private String reduceDue(String due) {
		if (due.charAt(due.length() - 1) == 'T')
			return reduceDue(due.substring(0, due.lastIndexOf(" ")));
		else if (due.charAt((due.length() - 1)) == 'M')
			return reduceDue(due.substring(0, due.lastIndexOf(" ")));
		else if (due.charAt(due.length() - 3) == ':' && due.charAt(due.length() - 6) == ':')
			return reduceDue(due.substring(0, due.lastIndexOf(":")));
		else
			return due;
	}

	public Instant getDueInstant() {
		return dueInstant;
	}

	public void setDueInstant(Instant dueInstant) {
		this.dueInstant = dueInstant;
	}

	private String enforceFormat(String due)  {
		try {
			this.dueInstant = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").parse(due).toInstant().minus(Duration.ofHours(5));
			return Date.from(dueInstant).toString();
		} catch (ParseException e) {
			e.printStackTrace();
		}

		return due;
	}

	public long getDueNum() {
		return dueNum;
	}

	private void setDueNum(String due) {
		int monthNum = 0, dayNum = 0, yearNum = 0, hourNum = 0;

		//October 1, 2020 11:59
		for (int i = 0; i < MONTHS.length; i++)
			if (due.startsWith(MONTHS[i])) {
				monthNum = i + 1;
				due = due.substring(due.indexOf(' ') + 1);
			}

		dayNum = Integer.parseInt(due.substring(0, due.indexOf(',')));
		due = due.substring(due.indexOf(' ') + 1);

		yearNum = Integer.parseInt(due.substring(0, due.indexOf(' ')));
		due = due.substring(due.indexOf(' ') + 1);

		int colonIndex = due.indexOf(":");
		hourNum = Integer.parseInt(due.substring(0, colonIndex) + due.substring(colonIndex + 1));

		this.dueNum = getDueNumFromInts(yearNum, monthNum, dayNum, hourNum);
	}

	private long getDueNumFromInts(int year, int month, int day, int hour) {
		return (long) ((year * Math.pow(10.0, 8.0)) + (month * Math.pow(10.0, 6.0)) + (day * Math.pow(10.0, 4.0)) + hour);
	}

	public void setDueNum(int dueNum) {
		this.dueNum = dueNum;
	}

	public void setDue(String due) {
		due = enforceFormat(due);

		this.due = due;

//		setDueNum(due);
	}

	@Override
	public int compareTo(Assignment assignment) {
		if (this.getDue() == null)
			return 1;
		else if (assignment.getDue() == null)
			return -1;

		boolean thisSubmitted = this.isSubmitted(), otherSubmitted = assignment.isSubmitted();
		if ((thisSubmitted && otherSubmitted) || (!thisSubmitted && !otherSubmitted)) {
			if (this.getDueInstant().compareTo(assignment.getDueInstant()) > 0)
				return 1;
			else if (this.getDueInstant().compareTo(assignment.getDueInstant()) < 0)
				return -1;
			else
				return 0;
		} else if (thisSubmitted && !otherSubmitted) {
			return 1;
		} else if (!thisSubmitted && otherSubmitted) {
			return -1;
		}


		return 0;
	}
}


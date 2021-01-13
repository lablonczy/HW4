import java.io.IOException;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;

public class Driver {

	//todo update sorting to include assns not turned in yet.
	public static void main(String[] args) {
		Date date = new Date();
		LocalDate localDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
		String month = localDate.getMonth().name();


		LinkedList<Assignment> assns;
		try {

			long bbTime = System.currentTimeMillis();

			BlackboardRetriever bb = new BlackboardRetriever();
			assns = bb.retrieve();

			bbTime = (System.currentTimeMillis() - bbTime);

			assns.forEach(System.out::println);

			System.out.println("found " + assns.size() + " blackboard assns in " + bbTime + "ms with " + BlackboardRetriever.reqs +" requests and " + BlackboardRetriever.avgReqTime / BlackboardRetriever.reqs + " ms per req");


		} catch (InterruptedException | IOException | URISyntaxException e) {
			e.printStackTrace();
		}

	}


}

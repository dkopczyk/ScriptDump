import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class TestMethodExtractorRunner {
  public static void main(String[] args) {
    String inputFilePath = "/Users/dorotakopczyk/Workspace/software_engineering_scripts/TestFinder/results/input.txt";
    String resultPath = "/Users/dorotakopczyk/Workspace/software_engineering_scripts/TestFinder/results/oneLiners.txt";

    try (BufferedReader reader = new BufferedReader(new FileReader(inputFilePath))) {
      String line;
      while ((line = reader.readLine()) != null) {
        String[] parts = line.split(",");
        if (parts.length == 2) {
          String path = parts[0].trim();
          String repoLink = parts[1].trim();
          System.out.println("Processing: " + path);

          // Call the TestMethodExtractor for the current path and repoLink
          TestMethodExtractor.extractAndAppendResults(path, repoLink, resultPath);
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}

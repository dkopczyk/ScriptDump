import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.TypeFilter;

public class TestMethodExtractor {
  public static void main(String[] args) {
    Launcher launcher = new Launcher();

    // What're you analyzing?
    String path = "/Users/dorotakopczyk/Workspace/steps-app/src/";
    String repoLink = "https://github.com/onaio/steps-app.git";
    String resultPath = "/Users/dorotakopczyk/Workspace/software_engineering_scripts/TestFinder/results/oneLiners.txt";
    launcher.addInputResource(path);

    // Run the analysis
    launcher.run();

    // Get the model
    CtModel model = launcher.getModel();
    Collection<CtMethod<?>> methods = model.getElements(e -> e instanceof CtMethod<?> &&
        ((CtMethod<?>) e).getAnnotations().stream()
            .anyMatch(annotation -> annotation.getAnnotationType().getSimpleName().equals("Test")));

    // Find and print @Test methods
    int oneAssertion = 0;
    int greaterThanOneAssertionNoVerify = 0;
    int verifyOnlyTest = 0;
    int comboTest = 0;
    int noNothing = 0;
    // For each test method we found, let's start analyzing it.

    for (CtMethod<?> testMethod : methods) {
      CtClass<?> whatClass = testMethod.getParent(CtClass.class);
      CtStatement body = testMethod.getBody();
      int assertCount = 0;
      int verifyCount = 0;
      System.out.println("Found @Test method: " + testMethod.getSimpleName() + " in " + whatClass.getSimpleName());

      // Analyze how often an assert or verify is called
      // CtInvocation is a Java method or constructor invocation
      List<CtInvocation> methodCalls = testMethod.getElements(new TypeFilter<>(CtInvocation.class));
      for (CtInvocation methodCall : methodCalls) {
        //System.out.println(methodCall);
        if(isAssertMethodCall(methodCall)){
          assertCount++;
        }
        if(isVerifyMethodCall(methodCall)){
          verifyCount++;
        }
      }

      // Summarize the results for the individual method
      System.out.println("Assert Count: " + assertCount + " Verify Count: " + verifyCount);
      if (assertCount == 1 && verifyCount == 0) {
        oneAssertion++;
        String data =  repoLink + "," + whatClass.getSimpleName() + "," + testMethod.getSimpleName() + "," + testMethod.getBody().toString().replace("\n", " ");
        writeToFile(resultPath, data);
        System.out.println(body);
      } else if (assertCount > 1 && verifyCount == 0) {
        greaterThanOneAssertionNoVerify++;
      } else if (assertCount == 0 && verifyCount > 0) {
        verifyOnlyTest++;
      } else if (assertCount > 0 && verifyCount > 0) {
        comboTest++;
      } else {
        noNothing++;
      }
    }

    System.out.println("Total number of tests in " + path + " is " + methods.size());
    System.out.println("Tests with just one assertion, no verify: " + oneAssertion);
    System.out.println("Tests with greater than one assertion, but no verify: " + greaterThanOneAssertionNoVerify);
    System.out.println("Tests with only verify: " + verifyOnlyTest);
    System.out.println("Tests with a combination of assert and verify: " + comboTest);
    System.out.println("Tests with nothing: " + noNothing);
  }

  private static boolean isAssertMethodCall(CtInvocation methodCall) {
    String methodCallAsString = methodCall.toString();
    return methodCallAsString.contains("org.junit.Assert");
  }

  private static boolean isVerifyMethodCall(CtInvocation methodCall) {
    String methodCallAsString = methodCall.toString();
    return methodCallAsString.contains("Mockito.verify");
  }

  private static void writeToFile(String fileName, String content) {
    try {
      FileWriter writer = new FileWriter(fileName, true);
      writer.write(content + "\n");
      writer.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}

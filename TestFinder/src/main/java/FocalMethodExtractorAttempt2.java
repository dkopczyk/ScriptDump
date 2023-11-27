import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtComment;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtTypeMember;
import spoon.reflect.visitor.CtScanner;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.compiler.ZipFolder;

public class FocalMethodExtractorAttempt2 {
  private static final int TOKEN_LIMIT = 1000;
  private static final String DELIMITER = "<FAZZINI>";

  public static void main(String[] args) {
    String sampleTests = "<path to txt file with test methods you need to extract >";
    try (BufferedReader reader = new BufferedReader(new FileReader(sampleTests))) {
      String line;
      while ((line = reader.readLine()) != null) {
        String[] columns = line.split(DELIMITER);

        // Extract the repo, the class with the test, and method we want to analyze and generate a TAP for
        String repoUrl = columns[0].trim();
        String testClassName = columns[1].trim();
        String testMethodName = columns[2].trim();

        String repoName = extractRepoName(repoUrl);
        String localDirectoryPath = "/Users/dorotakopczyk/Workspace/" + repoName;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(19);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.addInputResource(localDirectoryPath);
        // sudo find / -name "src.zip"
        launcher.addInputResource(new ZipFolder(new File("/usr/local/Homebrew/Cellar/openjdk/19.0.2/libexec/openjdk.jdk/Contents/Home/lib/src.zip")));
        // Add the Hamcrest source directory, it's matchers
        launcher.addInputResource("/Users/dorotakopczyk/Workspace/JavaHamcrest");
        launcher.buildModel();
        printTestInfo(localDirectoryPath, testClassName, testMethodName);

        CtModel model = launcher.getModel();
        CtClass<?> targetClass = findClass(model, testClassName);
        CtMethod<?> testMethod = findMethod(targetClass, testMethodName);
        if(testMethod == null){
          System.out.println("CAN'T FIND THE TEST?!");
          continue;
        }
        System.out.println("Test Body: " + testMethod.getBody());

        /*************** DELETE EXAMPLE BOILERPLATE TESTS ****************/
        if(testMethod.getSimpleName().equals("addition_isCorrect") && testClassName.equals("ExampleUnitTest")){
          System.out.println("Deleting fake addition_isCorrect test from " + repoName );
          removeLineFromFile(line);
          continue;
        }

        /******************************************* 3.2 Method Extraction ************************************************************/
        // Time to analyze if this test is eligible for becoming a TAP per the ATLAS model:
        // 1. Exclude test methods longer than 1,000 tokens.
        if(isTestMethodTooLong(testMethod)){
          System.out.println("THIS IS TOO LONG, REMOVE IT");
          continue;
        }

        // 2. After extracting the test methods, we extract every method declared within the project, excluding methods from third party libraries.
        List<CtMethod<?>> projectMethods = extractMethodsFromProject(model);
        /*
          The reason we only consider methods declared within the project is two-fold.
          First, most assert statements are evaluating the internal information
            of the project itself rather than information taken from third party
            libraries or external packages.
          Second, it would require a substantial
             effort to retrieve the method bodies and signatures from all the
             third party libraries and external packages.
         */

        // 3. Only one assert (the input file's already done that)
        /******************************************* 3.3 identifying focal methods ************************************************************/
        // Our next task is to identify the focal method that the assert statement, within the test method, is testing.

        // 4.We begin by extracting every method called within the test method
        List<CtMethod<?>> calledMethodsFromTestMethod = extractCalledMethods(testMethod);

        // 5. The list of invoked methods is then queried against the previously extracted list of methods defined inside the project, considering the complete method signature.
        List<CtMethod<?>> commonMethods = findCommonMethods(projectMethods, calledMethodsFromTestMethod);

        System.out.println("Common methods between project methods and the test method:");
        for (CtMethod<?> method : commonMethods) {
          System.out.println("  Method: " + method.getSignature());
        }
        System.out.println("Common method size: " + commonMethods.size() + ", calledMethods just from test size: " + calledMethodsFromTestMethod.size());
        // Find methods in calledMethodsFromTestMethod not in commonMethods
        // The second filtering step removes test methods in which the appropriate assert statement requires the synthesis of one or more unknown tokens. This means
        // that the syntactically and semantically correct assert statement requires a token that cannot be found in the vocabulary or in the contextual method (i.e., test method + focal method). Indeed, there
        // is no way to synthesize these tokens when the model attempts to generate a prediction. We further explain this problem as well as our developed solution in Section 3.4.1
        List<CtMethod<?>> methodsNotInCommon = findMethodsNotInCommon(calledMethodsFromTestMethod, commonMethods);
        if(!methodsNotInCommon.isEmpty()){
          System.out.println("Methods in calledMethodsFromTestMethod but not in commonMethods:");
          for (CtMethod<?> method : methodsNotInCommon) {
            System.out.println("  Method: " + method.getSignature());
          }
          continue;
        }

        // 7. Focal method heuristic:
        //  We then assume that the last method call before the assert is the focal method of the assert statement [29]. In some
        // instances, the assert statement contains the method call within its parameters. In these cases, we consider the method call within the
        // assertion parameters as the focal method.
        CtMethod<?> lastMethodFromList = getLastMethod(calledMethodsFromTestMethod);
        System.out.println("Last Method From List: " + lastMethodFromList);
        CtMethod<?> lastMethodFromCustom = getLastMethod(testMethod);
        System.out.println("Last Method Scanner: " + lastMethodFromCustom);

        // 8. We then proceed to remove the entire assert statement from the test method, replacing it with the unique token "AssertPlaceHolder".
        // DORT'S JUST EXTRACTING IT
        CtInvocation<?> assertCall = getAssertCall(testMethod);


        // 9. Write it back
        writeToOutputFile(repoUrl, testClassName, testMethod, lastMethodFromList, assertCall, line);
        System.out.println("...............................................................................................................");
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /*

  */
  static void writeToOutputFile(String repo, String testClassName, CtMethod<?> testMethod, CtMethod<?> focalMethod, CtInvocation<?> assertCall, String line){
    System.out.println("Repo: " + repo);
    System.out.println("Test Class" + testClassName);
    System.out.println("Test Body: " + testMethod.getBody());
    replaceComments(testMethod);
    System.out.println("Test Body Post Comment Replacement: " + testMethod.getBody());

    System.out.println("Focal Method Name: " + focalMethod.getSimpleName());
    System.out.println("Focal Method Body: " + focalMethod.getBody());
    replaceComments(focalMethod);
    System.out.println("Focal Method Body Post Comment Replacement: " + focalMethod.getBody());

    CtInvocation<?> focalMethodInvocation = findInvocationOfFocalMethod(testMethod, focalMethod);
    var parameters = focalMethodInvocation.getExecutable().getParameters();
    System.out.println("Focal Method Executable: " + focalMethodInvocation.getExecutable());
    System.out.println("Parameters: " + parameters);
    var arguments = focalMethodInvocation.getArguments();
    System.out.println("Arguments: " + arguments);
    System.out.println("Assert Line: " + assertCall.toString());

    String focalMethodBody = null; // See getAllAlbumsTest from AlbumServiceTest in FifthElement repo
    if(focalMethod.getBody() != null){
      focalMethodBody = focalMethod.getBody().toString();
    }

    writeToFile(repo,
        testClassName,
        testMethod.getSimpleName(),
        testMethod.getBody().toString(),
        focalMethod.getSimpleName(),
        focalMethodBody,
        focalMethodInvocation.getExecutable().toString(),
        (!arguments.isEmpty()) ? arguments.toString() : "none-fazzini",
        parameters.toString(),
        assertCall.toString());
    removeLineFromFile(line);
  }

  /*
    Repo	https://github.com/guliash/Calculator.git
    Test Class	PowTester
    Test Method Name	sqrtTest1
    Test Method Body	{ org.junit.Assert.assertEquals(java.lang.Math.sqrt(25), calculate("sqrt(25)"), com.guliash.parser.BaseParserTester.EPS }
    Focal Method Name	calculate
    Focal Method Body	{ return calculate(expression, new java.util.ArrayList<com.guliash.parser.StringVariable>(), com.guliash.parser.AngleUnits.RAD); }
    Focal Method Parameter	"sqrt(25)"
    Focal Method Executable	calculate(java.lang.String)
    Assert Line
   */
  private static void writeToFile(String repo, String testClassName, String testMethodName, String testMethodBody, String focalMethodName, String focalMethodBody,  String executable, String arguments, String parameters, String assertLine) {
    String outputPath = "<path to file youll generate taps from>";
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath, true))) {
      String tmBody = testMethodBody.replace("\n", "");
      String fmBody = null;
      if(focalMethodBody != null){
        fmBody = focalMethodBody.replace("\n", "");
      }
      String aBody = assertLine.replace("\n", "");
      String modifiedLine = repo + DELIMITER +
                            testClassName + DELIMITER +
                            testMethodName + DELIMITER +
                            tmBody + DELIMITER +
                            focalMethodName + DELIMITER +
                            fmBody + DELIMITER +
                            arguments + DELIMITER +
                            executable + DELIMITER +
                            parameters + DELIMITER +
                            aBody + DELIMITER + "\n";
      writer.write(modifiedLine);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static void removeLineFromFile(String lineToRemove) {
    String filePath = "/Users/dorotakopczyk/Workspace/software_engineering_scripts/sample_picker/sampled_dataCherry.txt";
    try {
      BufferedReader reader = new BufferedReader(new FileReader(filePath));
      String currentLine;
      List<String> lines = new ArrayList<>();

      while ((currentLine = reader.readLine()) != null) {
        if (!currentLine.equals(lineToRemove)) {
          lines.add(currentLine);
        }
      }
      reader.close();

      BufferedWriter writer = new BufferedWriter(new FileWriter(filePath));
      for (String line : lines) {
        writer.write(line + "\n");
      }
      writer.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static void replaceComments(CtMethod<?> method) {
    // Find all comments in the method
    List<CtComment> comments = method.getElements(new TypeFilter<>(CtComment.class));

    // Iterate through comments and replace single-line comments with block comments
    for (CtComment comment : comments) {
      if (comment.getContent().startsWith("//")) {
        // Replace single-line comment with block comment
        comment.replace(method.getFactory().Code().createComment("/*" + comment.getContent() + "*/", CtComment.CommentType.BLOCK));
      }
    }
  }

  private static CtInvocation<?> findInvocationOfFocalMethod(CtMethod<?> testMethod, CtMethod<?> focalMethod) {
    List<CtInvocation> methodCalls = testMethod.getElements(new TypeFilter<>(CtInvocation.class));

    for (CtInvocation<?> invocation : methodCalls) {
      System.out.println("invocation: " + invocation);
      if (invocation.getExecutable().getExecutableDeclaration() == focalMethod) {
        return invocation;
      }
    }
    // no matching invocation is found
    throw new RuntimeException("How'd this happen?!");
  }

  private static CtInvocation<?> getAssertCall(CtMethod<?> method){
    List<CtInvocation> methodCalls = method.getElements(new TypeFilter<>(CtInvocation.class));
    for (CtInvocation invocation : methodCalls){
      if (isAssertMethodCall(invocation)){
        System.out.println(invocation);
        return invocation;
      }
    }

    throw new RuntimeException("Couldnt find assert line?!");
  }
  private static CtMethod<?> getLastMethod(CtMethod<?> method) {
    // Use a custom CtScanner to find the last method invocation
    LastMethodScanner scanner = new LastMethodScanner();
    method.accept(scanner);

    return scanner.getLastMethod();
  }

  private static class LastMethodScanner extends CtScanner {
    private CtMethod<?> lastMethod;

    public CtMethod<?> getLastMethod() {
      return lastMethod;
    }

    @Override
    public <T> void visitCtInvocation(CtInvocation<T> invocation) {
      CtExecutable<?> executable = invocation.getExecutable().getExecutableDeclaration();
      if (executable instanceof CtMethod<?>) {
        lastMethod = (CtMethod<?>) executable;
      }
      super.visitCtInvocation(invocation);
    }
  }
  private static CtMethod<?> getLastMethod(List<CtMethod<?>> methodList) {
    int size = methodList.size();

    if (size > 0) {
      return methodList.get(size - 1);
    } else {
      // Return null or handle the case when the list is empty
      System.out.println("HOW DID YOU GET HERE?!?");
      return null;
    }
  }

  private static List<CtMethod<?>> findMethodsNotInCommon(List<CtMethod<?>> list1, List<CtMethod<?>> list2) {
    List<CtMethod<?>> notInCommon = new ArrayList<>(list1);
    notInCommon.removeAll(list2);
    return notInCommon;
  }
  private static List<CtMethod<?>> findCommonMethods(List<CtMethod<?>> list1, List<CtMethod<?>> list2) {
    List<CtMethod<?>> commonMethods = new ArrayList<>(list1);
    commonMethods.retainAll(list2);
    return commonMethods;
  }

  private static List<CtMethod<?>> extractMethodsFromProject(CtModel model) {
    List<CtMethod<?>> projectMethods = new ArrayList<>();

    for (CtClass<?> ctClass : model.getElements(new TypeFilter<>(CtClass.class))) {
        projectMethods.addAll(extractMethodsFromClass(ctClass));
    }
    return projectMethods;
  }



  private static List<CtMethod<?>> extractMethodsFromClass(CtClass<?> ctClass) {
    List<CtMethod<?>> classMethods = new ArrayList<>();

    for (CtMethod<?> method : ctClass.getMethods()) {
        classMethods.add(method);
    }

    return classMethods;
  }

  private static boolean isTestMethodTooLong(CtMethod<?> method) {
    // Calculate the token count of the method body
    int tokenCount = calculateTokenCountInBlock(method.getBody());

    // Check if the method exceeds the token limit
    return tokenCount > TOKEN_LIMIT;
  }

  private static int calculateTokenCountInBlock(CtBlock<?> block) {
    int tokenCount = 0;
    if (block != null) {
      for (CtStatement statement : block.getStatements()) {
        if (statement instanceof CtTypeMember) {
          tokenCount += calculateTokenCount((CtTypeMember) statement);
        } else {
          tokenCount += statement.getPosition().getEndLine() - statement.getPosition().getLine() + 1;
        }
      }
    }
    return tokenCount;
  }

  private static int calculateTokenCount(CtTypeMember typeMember) {
    // Calculate token count recursively for the type member and its children
    int tokenCount = 0;
    if (typeMember != null) {
      if (typeMember instanceof CtBlock) {
        CtBlock<?> block = (CtBlock<?>) typeMember;
        tokenCount += calculateTokenCountInBlock(block);
      } else {
        tokenCount += typeMember.getPosition().getEndLine() - typeMember.getPosition().getLine() + 1;
      }

      for (CtTypeMember child : typeMember.getElements(new TypeFilter<>(CtTypeMember.class))) {
        tokenCount += calculateTokenCount(child);
      }
    }
    return tokenCount;
  }


  private static boolean isAssertMethodCall(CtInvocation methodCall) {
    String methodCallAsString = methodCall.toString();
    return methodCallAsString.contains("org.junit.Assert");
  }

  private static String extractRepoName(String repoUrl) {
    String[] parts = repoUrl.split("/");
    String repoFullName = parts[parts.length - 1];
    return repoFullName.replace(".git", "");
  }

  /**
   * Given a model and class name, return the CtClass
   * @param model
   * @param className
   * @return
   */
  private static CtClass<?> findClass(CtModel model, String className) {
    var x = model.getElements(e -> e instanceof CtClass<?>
        && ((CtClass<?>) e).isClass()
        && ((CtClass<?>) e).getSimpleName().equals(className));
    return (CtClass<?>)  x.get(0);
  }

  private static CtMethod<?> findMethod(CtClass<?> targetClass, String methodName) {
    var methods = targetClass.getAllMethods();

    for (CtMethod<?> method : methods) {
      if (method.getSimpleName().equals(methodName)) {
        return method;
      }
    }

    return null;
  }


  private static void printTestInfo(String localDirectoryPath, String testClassName, String testMethodName) {
    System.out.println("Local Directory Path: " + localDirectoryPath);
    System.out.println("Test Class Name: " + testClassName);
    System.out.println("Test Method Name: " + testMethodName);
  }

  public static class MethodInvocationExtractor extends CtScanner {
    private CtInvocation<?> methodInvocation;

    public void processExpression(CtExpression<?> expression) {
      expression.accept(this);
    }

    public CtInvocation<?> getMethodInvocation() {
      return methodInvocation;
    }

    @Override
    public <T> void visitCtInvocation(CtInvocation<T> invocation) {
      // Assuming you want to find the first invocation in the expression
      if (methodInvocation == null) {
        methodInvocation = invocation;
      }
      super.visitCtInvocation(invocation);
    }
  }

  private static List<CtMethod<?>> extractCalledMethods(CtMethod<?> method) {
    MethodCallExtractor methodCallExtractor = new MethodCallExtractor();
    method.accept(methodCallExtractor);
    return methodCallExtractor.getCalledMethods();
  }


  private static class MethodCallExtractor extends CtScanner {
    private List<CtMethod<?>> calledMethods = new ArrayList<>();

    public List<CtMethod<?>> getCalledMethods() {
      return calledMethods;
    }

    @Override
    public <T> void visitCtInvocation(CtInvocation<T> invocation) {
      // Get the executable and check if it's a CtMethod
      CtExecutable<?> executable = invocation.getExecutable().getExecutableDeclaration();
      if (executable instanceof CtMethod<?>) {
        CtMethod<?> calledMethod = (CtMethod<?>) executable;

        // Exclude methods from org.junit.Assert
        if (!isJUnitAssertMethod(calledMethod)) {
          calledMethods.add(calledMethod);
        }
      }

      // Continue visiting nested invocations
      super.visitCtInvocation(invocation);
    }

    private boolean isJUnitAssertMethod(CtMethod<?> method) {
      // Check if the method is from org.junit.Assert
      return method.getDeclaringType() != null
          && method.getDeclaringType().getQualifiedName().startsWith("org.junit.Assert");
    }
  }
}


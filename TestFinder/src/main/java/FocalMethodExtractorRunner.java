import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.CtScanner;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.compiler.ZipFolder;

public class FocalMethodExtractorRunner {
  public static void main(String[] args) {
    String sampleTests = "<sampled_tests_path>.txt";
    try (BufferedReader reader = new BufferedReader(new FileReader(sampleTests))) {
      String line;
      while ((line = reader.readLine()) != null) {
        String[] columns = line.split("<FAZZINI>");
        String repoUrl = columns[0].trim();
        String testClassName = columns[1].trim();
        String testMethodName = columns[2].trim();

        String repoName = extractRepoName(repoUrl);
        String localDirectoryPath = "/Users/dorotakopczyk/Workspace/" + repoName;

        Launcher launcher = new Launcher();

        launcher.addInputResource(localDirectoryPath);


        launcher.buildModel();

        printTestInfo(localDirectoryPath, testClassName, testMethodName);

        CtModel model = launcher.getModel();
        CtClass<?> targetClass = findClass(model, testClassName);
        CtMethod<?> targetMethod = findMethod(targetClass, testMethodName);
        System.out.println("Test Body: " + targetMethod.getBody());
        List<CtInvocation> methodCalls = targetMethod.getElements(new TypeFilter<>(CtInvocation.class));

        CtInvocation previousMethodCall = null;
        for (CtInvocation methodCall : methodCalls) {
          if (isAssertMethodCall(methodCall)) {
            CtExecutableReference<?> executable = methodCall.getExecutable();
            String methodName = executable.getSimpleName();
            if("assertThat".equals(methodName)){
              List<CtExpression<?>> arguments = methodCall.getArguments();
              System.out.println("Arg size: " + arguments.size());
              System.out.println("Args: " + arguments);
              var expectationArg = arguments.get(0);
            }
            if("assertNull".equals(methodName)){
              List<CtExpression<?>> arguments = methodCall.getArguments();
              System.out.println("Arg size: " + arguments.size());
              System.out.println("Args: " + arguments);
              var expectationArg = arguments.get(0);
              if (expectationArg instanceof CtInvocation) {
                CtInvocation<?>  candidateFocalMethod = (CtInvocation<?>)  expectationArg;
                var executableReference =candidateFocalMethod.getExecutable();
                String focalMethodName = executableReference.getSimpleName();
                System.out.println("Candidate Focal Method: " + candidateFocalMethod.getExecutable().getSimpleName());

                writeToOutputFile(line, focalMethodName, executableReference, candidateFocalMethod.getArguments(), model);
              } else if (expectationArg instanceof CtVariable) {
                CtVariable<?> variable = (CtVariable<?>) expectationArg;
                System.out.println("ExpectationArg is a variable: " + variable);
                return;
              } else {
                System.out.println("ExpectationArg is neither an invocation nor a variable");
                return;
              }
            }
            // assert equals time
            if("assertEquals".equals(methodName)){
              List<CtExpression<?>> arguments = methodCall.getArguments();
              System.out.println("Arg size: " + arguments.size());
              System.out.println("Args: " + arguments);
              // First argument is expected Second argument is actual Third argument is a message
              // assertEquals(float expected, float actual, float delta, Supplier<String> messageSupplier)

              var actualArg = arguments.get(1);
              System.out.println("Arg Actual: " + actualArg.toString());
              if (actualArg instanceof CtInvocation){
                // Actual had a method call in it
                CtInvocation<?>  candidateFocalMethod = (CtInvocation<?>)  actualArg;
                System.out.println("Candidate Focal Method, lots of chaining: " + candidateFocalMethod);
                String focalMethodName = candidateFocalMethod.getExecutable().getSimpleName();
                System.out.println("Candidate Focal Method, lots of chaining, name:  " + focalMethodName);
                writeToOutputFile(line, focalMethodName, candidateFocalMethod.getExecutable(), candidateFocalMethod.getArguments(), model);
              }
              else {
                System.out.println("The actual parameter inside the equals was not evaluated to be an invocation");

                String focalMethodName = null;
                CtExecutableReference<?> executableReference = null;
                List<CtExpression<?>> parameters = null;
                MethodInvocationExtractor extractor = new MethodInvocationExtractor();
                extractor.processExpression(actualArg);
                CtInvocation<?> methodInvocation = extractor.getMethodInvocation();
                if (methodInvocation != null) {
                  // You've found the method invocation
                  System.out.println("Method Invocation: " + methodInvocation);
                  focalMethodName = methodInvocation.getExecutable().getSimpleName();
                  executableReference = methodInvocation.getExecutable();
                  parameters = methodInvocation.getArguments();
                  System.out.println("Params: " + parameters);
                } else {
                  System.out.println("No method invocation found in the expression. Dort, you need to figure out how to set just hardcoded values");
                  return;
                }

                writeToOutputFile(line, focalMethodName, executableReference, parameters, model);
              }
            }
            //Check if the focal method is 'assertTrue' or 'assertFalse' and has the expected condition
            if ("assertTrue".equals(methodName) || "assertFalse".equals(methodName)) {

              List<CtExpression<?>> arguments = methodCall.getArguments();
              System.out.println("Arg size: " + arguments.size());

              // Assuming the second argument is the focal point
              if (arguments.size() > 1) {
                CtExpression<?> condition = arguments.get(1);
                System.out.println("Assert True, second arg: " + condition);
                // Check if the condition is a method invocation
                if (condition instanceof CtInvocation) {
                  CtInvocation candidateFocalMethod = (CtInvocation) condition;
                  String focalMethodName = candidateFocalMethod.getExecutable().getSimpleName();
                  var executableReference = candidateFocalMethod.getExecutable();


                  String equalsSourceCode = candidateFocalMethod.getExecutable().getExecutableDeclaration().toString();

                  System.out.println("Candidate Focal Method: " + focalMethodName);
                  System.out.println("Candidate Focal Executable: " + candidateFocalMethod.getExecutable());
                  System.out.println("Candidate Focal Method Body: " + equalsSourceCode);
                  writeToOutputFile(line, focalMethodName, candidateFocalMethod.getExecutable(), candidateFocalMethod.getArguments(), model);
                }
                else{
                    System.out.println("Condition: " + condition.prettyprint());
                    CtInvocation candidateFocalMethod = null;
                    String focalMethodName = null;
                    if(previousMethodCall != null){
                      candidateFocalMethod = previousMethodCall;
                      System.out.println("Previous Call: " + previousMethodCall.prettyprint());
                      System.out.println("Candidate Focal Method: " + candidateFocalMethod.getExecutable().getSimpleName());
                      focalMethodName = candidateFocalMethod.getExecutable().getSimpleName();
                      // Assuming the first argument of equals is the parameter
                      if(candidateFocalMethod.getArguments().size() > 1)
                      {
                        System.out.println("Dort, you need to handle this case!");
                        return;
                      }
                      writeToOutputFile(line, focalMethodName, candidateFocalMethod.getExecutable(), candidateFocalMethod.getArguments(), model);
                    }
                    else {
                      System.out.println("Dort, you need to handle this case!");
                      return;
                    }
                }
              }
              if(arguments.size() == 1){
                CtExpression<?> condition = arguments.get(0);
                System.out.println(condition);
                if (condition instanceof CtInvocation){
                  CtInvocation candidateFocalMethod = (CtInvocation) condition;
                  String focalMethodName = candidateFocalMethod.getExecutable().getSimpleName();

                  writeToOutputFile(line, focalMethodName, candidateFocalMethod.getExecutable(), candidateFocalMethod.getArguments(), model);
                }
              }
            }
          }
          previousMethodCall = methodCall;
        }

        System.out.println("...............................................................................................................");

      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static void writeToOutputFile(String line, String focalMethodName, CtExecutableReference<?> executable, List<CtExpression<?>> parameters, CtModel model) {
    System.out.println("Executable: " + executable);
    CtTypeReference<?> declaringType = executable.getDeclaringType();
    if(declaringType == null){
      System.out.println("Deal with this later");
      return;
    }
    String focalClassName = declaringType.getSimpleName();
    System.out.println("Declaring Type: " + executable.getDeclaringType());
    System.out.println("Focal Class Name: " + focalClassName );
    String packageName = declaringType.getPackage() != null ? declaringType.getPackage().getQualifiedName() : "";
    String methodBody;


    if (packageName.startsWith("java.")) { // Anything from the Java JDK is a terrible hack
      System.out.println("Declaring type is from the JDK.");
      methodBody = inspectJDKClass(executable.getDeclaringType());
    } else {
      System.out.println("Declaring type is from the analyzed source code (repository).");
      var focalClass = findClass(model, focalClassName);
      var actualMethod = findMethod(focalClass, focalMethodName).getReference();
      methodBody = actualMethod.getExecutableDeclaration() != null
          ? executable.getExecutableDeclaration().getBody().toString()
          : null;
    }

    String parametersX = null;
    if(parameters.size() > 0){
      parametersX = parameters.toString();
    }

    System.out.println("Focal Method: " + focalMethodName);
    System.out.println("Executable: " + executable);
    System.out.println("MethodBody: " + methodBody);
    System.out.println("Parameters: " + ( parametersX != null ? parametersX: "none-fazzini"));


    String outputPath = "/Users/dorotakopczyk/Workspace/software_engineering_scripts/sample_picker/sampled_data_worked_attempt3.txt";
    writeToFile(outputPath, line, focalMethodName, methodBody, (parametersX != null) ? parametersX : "none-fazzini", executable.toString());
    removeLineFromFile(line);
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

  private static void writeToFile(String filePath, String line, String focalMethodName, String methodBody, String parameters, String executable) {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, true))) {
      String mbody = null;
      if(methodBody != null){
        mbody = methodBody.replace("\n", "");
      }
      String modifiedLine = line +  focalMethodName + "<FAZZINI>" + mbody + "<FAZZINI>" + parameters + "<FAZZINI>" + executable + "<FAZZINI>" + "\n";
      writer.write(modifiedLine);
    } catch (IOException e) {
      e.printStackTrace();
    }
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

  public static String inspectJDKClass(CtTypeReference<?> typeReference) {
    System.out.println(typeReference);
    CtType<?> type = typeReference.getTypeDeclaration();
    var className = type.getQualifiedName();

    // Print information about the class
    System.out.println("Class Name: " + className);

    // Inspect methods
    for (CtMethod<?> method : type.getMethods()) {
      if(method.getSimpleName().equals("equals") && className.equals("java.lang.String")){
        // https://developer.classpath.org/doc/java/lang/String-source.html
        System.out.println("Method Name: " + method.getSimpleName());
        System.out.println("Method Source Code:\n" + method.getBody().toString());
        System.out.println("--------------------------------------------------------");
        // THIS IS A HACK, I CANT GET JDK DYNAMIC CODE TO SHOW UP
        return "public boolean equals(Object anObject) {\n" +
            "        if (this == anObject) {\n" +
            "            return true;\n" +
            "        }\n" +
            "        return (anObject instanceof String aString)\n" +
            "                && (!COMPACT_STRINGS || this.coder == aString.coder)\n" +
            "                && StringLatin1.equals(value, aString.value);\n" +
            "    }";
      }
    }

    return null;
  }

  public static void printChaining(CtExpression<?> expression) {
    if (expression instanceof CtInvocation) {
      CtInvocation<?> invocation = (CtInvocation<?>) expression;
      System.out.println("Method Name: " + invocation.getExecutable().getSimpleName());
      // Check if there's more chaining
      CtExpression<?> target = invocation.getTarget();
      if (target instanceof CtInvocation) {
        System.out.println("Chaining detected:");
        printChaining(target);
      } else {
        System.out.println("End of chaining");
      }
    } else {
      System.out.println("Not a method invocation");
    }
  }

  private static CtMethod<?> findMethodDeclarationRoundWhatever(CtModel model, String methodName) {
    // Iterate over all classes in the model
    for (CtClass<?> ctClass : model.getElements(new TypeFilter<>(CtClass.class))) {
      System.out.println(ctClass);
      // Iterate over all methods in the class
      for (CtMethod<?> ctMethod : ctClass.getMethods()) {
        // Check if the method has the desired name
        if (ctMethod.getSimpleName().equals(methodName)) {
          return ctMethod;
        }
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
}

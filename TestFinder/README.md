# Extract Test Method En Bulk From a Repo 
Given a repository you manually set in the `path` of `TestMethodExtractor.java`, analyze all unit tests of the repository using the [Spoon](https://github.com/INRIA/spoon) library. 

In particular, count the various types of asserts a unit test can have. Should a unit test only have one simple assert, save off some information for future scrutiny. 

## To Run:
### From IntelliJ
+ Open the project
+ On the `TestMethodExtractor` file, double click and Build
+ On the `TestMethodExtractor` file, double click and Run
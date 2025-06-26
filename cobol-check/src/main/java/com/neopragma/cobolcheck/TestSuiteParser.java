/*
Copyright 2020 David Nicolette

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package com.neopragma.cobolcheck;

import com.neopragma.cobolcheck.exceptions.PossibleInternalLogicErrorException;
import com.neopragma.cobolcheck.exceptions.TestSuiteCouldNotBeReadException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Parses the concatenated test suite and writes Cobol test code to the output stream for the generated test program.
 *
 * @author Dave Nicolette (neopragma)
 * @since 14
 */
public class TestSuiteParser implements StringHelper {
    private final KeywordExtractor keywordExtractor;
    private final Messages messages;
    private List<String> testSuiteTokens;

    // Source tokens used in fully-qualified data item names
    private final List<String> qualifiedNameKeywords = Arrays.asList("IN", "OF");


    // Optionally replace identifier prefixes in cobol-check copybook lines and generated source lines,
    // in case of conflict with prefixes used in programs to be tested.
    // This is set in config.properties, cobolcheck.prefix entry.
    private final String testCodePrefix;

    // Flags to keep track of context while reading input source files.
    // We want to make a single pass of all inputs, so we need to know what we are looking for at any given point.
    private boolean emptyTestSuite;
    private boolean cobolStatementInProgress;
    private boolean expectInProgress;
    private boolean toBeInProgress;
    private boolean boolean88LevelCompare;
    private boolean expectTestsuiteName;
    private boolean expectTestcaseName;
    private String fieldNameForExpect;
    private boolean possibleQualifiedName;
    private boolean expectQualifiedName;
    private String expectedValueToCompare;
    private boolean reverseCompare;
    private boolean greaterThanComparison;
    private boolean lessThanComparison;
    private KeywordAction nextAction = KeywordAction.NONE;
    private String currentTestSuiteName = Constants.EMPTY_STRING;
    private String currentTestCaseName = Constants.EMPTY_STRING;

    // Lines inserted into the test program
    private static final String COBOL_PERFORM_INITIALIZE =
            "           PERFORM %sINITIALIZE";
    private static final String COBOL_DISPLAY_TESTSUITE =
            "           DISPLAY \"TESTSUITE:\"                                                 ";
    private static final String COBOL_DISPLAY_NAME =
            "           DISPLAY %s";
    private static final String COBOL_STORE_TESTCASE_NAME_1 =
            "           MOVE %s";
    private static final String COBOL_STORE_TESTCASE_NAME_2 =
            "               TO %sTEST-CASE-NAME";
    private static final String COBOL_PERFORM_BEFORE =
            "           PERFORM %sBEFORE";
    private static final String COBOL_INCREMENT_TEST_CASE_COUNT =
            "           ADD 1 TO %sTEST-CASE-COUNT";

    /**
     * Example: This will look like:
     * SET UT-NORMAL-COMPARE TO TRUE
     * or when NOT is specified:
     * SET UT-REVERSE-COMPARE TO TRUE
     */
    private static final String COBOL_SET_NORMAL_OR_REVERSE_COMPARE =
            "           SET %1$s%2$s-COMPARE TO %3$s";

    private static final String COBOL_SET_COMPARE_NUMERIC =
            "           SET %1$sNUMERIC-COMPARE TO %2$s";
    private static final String COBOL_SET_COMPARE_88_LEVEL =
            "           SET %1$sCOMPARE-88-LEVEL TO %2$s";

    /**
     * Example: This will look like:
     * SET UT-RELATION-GT for "greater than"
     * SET UT-RELATION-LT for "less than"
     * SET UT-RELATION-EQ for "equal to"
     */
    private static final String COBOL_SET_RELATION =
            "           SET %1$sRELATION-%2$s TO %3$s";

    private static final String COBOL_MOVE_FIELDNAME_TO_ACTUAL =
            "           MOVE %2$s TO %1$sACTUAL";
    private static final String COBOL_MOVE_FIELDNAME_TO_ACTUAL_NUMERIC =
            "           MOVE %2$s TO %1$sACTUAL-NUMERIC";
    private static final String COBOL_MOVE_EXPECTED_ALPHANUMERIC_LITERAL_1 =
            "           MOVE %s";
    private static final String COBOL_MOVE_EXPECTED_ALPHANUMERIC_LITERAL_2 =
            "               TO %sEXPECTED";
    private static final String COBOL_MOVE_EXPECTED_NUMERIC_LITERAL =
            "           MOVE %2$s TO %1$sEXPECTED-NUMERIC";
    private static final String COBOL_SET_ACTUAL_88_VALUE_1 =
            "           IF %1$s";
    private static final String COBOL_SET_ACTUAL_88_VALUE_2 =
            "               SET %1$sACTUAL-88-VALUE TO TRUE";
    private static final String COBOL_SET_ACTUAL_88_VALUE_3 =
            "               MOVE 'TRUE' TO %1$sACTUAL";
    private static final String COBOL_SET_ACTUAL_88_VALUE_4 =
            "           ELSE";
    private static final String COBOL_SET_ACTUAL_88_VALUE_5 =
            "               SET %1$sACTUAL-88-VALUE TO FALSE";
    private static final String COBOL_SET_ACTUAL_88_VALUE_6 =
            "               MOVE 'FALSE' TO %1$sACTUAL";
    private static final String COBOL_SET_ACTUAL_88_VALUE_7 =
            "           END-IF";

    private static final String COBOL_SET_EXPECTED_88_VALUE_1 =
            "           IF %1$sEXPECTED-88-VALUE";
    private static final String COBOL_SET_EXPECTED_88_VALUE_2 =
            "               MOVE 'TRUE' TO %1$sEXPECTED";
    private static final String COBOL_SET_EXPECTED_88_VALUE_3 =
            "           ELSE";
    private static final String COBOL_SET_EXPECTED_88_VALUE_4 =
            "               MOVE 'FALSE' TO %1$sEXPECTED";
    private static final String COBOL_SET_EXPECTED_88_VALUE_5 =
            "           END-IF";
    private static final String COBOL_SET_EXPECTED_88_VALUE =
            "           SET %1$sEXPECTED-88-VALUE TO %2$s";
    private static final String COBOL_SET_ALPHANUMERIC_COMPARE =
            "           SET %1$sALPHANUMERIC-COMPARE TO %2$s";
    private static final String COBOL_CHECK_EXPECTATION =
            "           PERFORM %sCHECK-EXPECTATION";
    private static final String COBOL_PERFORM_AFTER =
            "           PERFORM %sAFTER";
    private static final String ELEVEN_LEADING_SPACES = "           ";

    private static final String RELATION_EQ = "EQ";
    private static final String RELATION_GT = "GT";
    private static final String RELATION_LT = "LT";
    private static final String NORMAL = "NORMAL";
    private static final String REVERSE = "REVERSE";

    private StringBuffer cobolStatement;
    private NumericFields numericFields;

    public TestSuiteParser(
            KeywordExtractor keywordExtractor,
            Config config) {
        this.keywordExtractor = keywordExtractor;
        this.messages = config.getMessages();
        testSuiteTokens = new ArrayList<>();
        emptyTestSuite = true;
        testCodePrefix = config.getString(Constants.COBOLCHECK_PREFIX_CONFIG_KEY, Constants.DEFAULT_COBOLCHECK_PREFIX);
        initializeCobolStatement();
    }

    /**
     * Process the test suite as a series of tokens. When we have processed all the input, getNextTokenFromTestSuite()
     * returns a null reference.
     *
     * @param testSuiteReader - reader attached to the concatenated test suite files.
     * @param testSourceOut   - writer attached to the test program being generated.
     */
    void parseTestSuite(BufferedReader testSuiteReader,
                        Writer testSourceOut,
                        NumericFields numericFieldsList) throws IOException {
        numericFields = numericFieldsList;
        String testSuiteToken = getNextTokenFromTestSuite(testSuiteReader);
        while (testSuiteToken != null) {
            if (!testSuiteToken.startsWith(Constants.QUOTE) && !testSuiteToken.startsWith(Constants.APOSTROPHE)) {
                testSuiteToken = testSuiteToken.toUpperCase(Locale.ROOT);
            }

            Keyword keyword = Keywords.getKeywordFor(testSuiteToken);
            Log.debug("Generator.parseTestSuite(), " +
                    "testSuiteToken <" + testSuiteToken + ">, \tkeyword.value() <" + keyword.value() + ">");

            // take actions triggered by the type of the current token
            switch (keyword.value()) {
                case Constants.TESTSUITE_KEYWORD:
                    expectTestsuiteName = true;
                    break;

                case Constants.TESTCASE_KEYWORD:
                    expectTestcaseName = true;
                    break;

                case Constants.EXPECT_KEYWORD:
                    if (cobolStatementInProgress) {
                        insertUserWrittenCobolStatement(testSourceOut);
                    }
                    cobolStatementInProgress = false;
                    initializeCobolStatement();
                    insertIncrementTestCaseCount(testSourceOut);
                    expectInProgress = true;
                    reverseCompare = false;
                    fieldNameForExpect = Constants.EMPTY_STRING;
                    break;

                case Constants.NOT_KEYWORD:
                    reverseCompare = true;
                    break;

                case Constants.NOT_EQUAL_SIGN_KEYWORD:
                    toBeInProgress = true;
                    // this means the user wrote "NOT !="
                    reverseCompare = !reverseCompare;
                    break;

                case Constants.GREATER_THAN_SIGN_KEYWORD:
                    toBeInProgress = true;
                    greaterThanComparison = true;
                    break;

                case Constants.LESS_THAN_SIGN_KEYWORD:
                    toBeInProgress = true;
                    lessThanComparison = true;
                    break;

                case Constants.GREATER_THAN_EQUAL_TO_SIGN_KEYWORD:
                    toBeInProgress = true;
                    lessThanComparison = true;
                    if (reverseCompare) {
                        reverseCompare = false;
                    } else {
                        reverseCompare = true;
                    }
                    break;

                case Constants.LESS_THAN_EQUAL_TO_SIGN_KEYWORD:
                    toBeInProgress = true;
                    greaterThanComparison = true;
                    reverseCompare = !reverseCompare;
                    break;

                case Constants.COBOL_TOKEN:
                    if (expectQualifiedName) {
                        fieldNameForExpect += testSuiteToken;
                        expectQualifiedName = false;
                    }
                    if (possibleQualifiedName) {
                        if (qualifiedNameKeywords.contains(testSuiteToken)) {
                            fieldNameForExpect += Constants.SPACE + testSuiteToken + Constants.SPACE;
                            expectQualifiedName = true;
                            possibleQualifiedName = false;
                        }
                    }
                    if (expectInProgress) {
                        fieldNameForExpect = testSuiteToken;
                        expectInProgress = false;
                        possibleQualifiedName = true;
                    }
                    if (toBeInProgress) {
                        expectedValueToCompare = testSuiteToken;
                        insertTestCodeForAssertion(testSourceOut, numericFields);
                        toBeInProgress = false;
                    }
                    break;

                case Constants.ALPHANUMERIC_LITERAL_KEYWORD:
                    if (expectTestsuiteName) {
                        expectTestsuiteName = false;
                        currentTestSuiteName = testSuiteToken;
                        insertTestSuiteNameIntoTestSource(currentTestSuiteName, testSourceOut);
                        initializeCobolStatement();
                    }
                    if (expectTestcaseName) {
                        expectTestcaseName = false;
                        currentTestCaseName = testSuiteToken;
                        insertTestCaseNameIntoTestSource(currentTestCaseName, testSourceOut);
                        initializeCobolStatement();
                    }
                    if (toBeInProgress) {
                        if (testSuiteToken.startsWith(Constants.QUOTE) || testSuiteToken.startsWith(Constants.APOSTROPHE)) {
                            expectedValueToCompare = testSuiteToken;
                            insertTestCodeForAssertion(testSourceOut, numericFields);
                        }
                        toBeInProgress = false;
                    }
                    break;

                case Constants.NUMERIC_LITERAL_KEYWORD:
                    if (toBeInProgress) {
                        expectedValueToCompare = testSuiteToken;
                        insertTestCodeForAssertion(testSourceOut, numericFields);
                        toBeInProgress = false;
                    }
                    break;

                case Constants.BOOLEAN_VALUE:
                    if (toBeInProgress) {
                        boolean88LevelCompare = true;
                        expectedValueToCompare = testSuiteToken;
                        insertTestCodeForAssertion(testSourceOut, numericFields);
                        boolean88LevelCompare = false;
                        toBeInProgress = false;
                    } else {
                        if (cobolStatementInProgress) {
                            appendTokenToCobolStatement(testSuiteToken);
                            insertUserWrittenCobolStatement(testSourceOut);
                            initializeCobolStatement();
                        }
                        cobolStatementInProgress = false;
                    }
                    break;

                case Constants.TO_BE_KEYWORD:
                case Constants.TO_EQUAL_KEYWORD:
                case Constants.EQUAL_SIGN_KEYWORD:
                    toBeInProgress = true;
                    break;
            }

            // take actions that were triggered by the previous token's action, pertaining to the current token
            switch (nextAction) {
                case TESTSUITE_NAME:
                    currentTestSuiteName = testSuiteToken;
                    nextAction = KeywordAction.NONE;
                    break;
            case NEW_TESTCASE:
                    currentTestCaseName = testSuiteToken;
                    nextAction = KeywordAction.NONE;
                    break;
            }

            // take actions that are triggered by the current token's action
            switch (keyword.keywordAction()) {
                case COBOL_STATEMENT:
                    if (CobolVerbs.isCobolVerb(testSuiteToken)) {
                        if (cobolStatementInProgress) {
                            insertUserWrittenCobolStatement(testSourceOut);
                            initializeCobolStatement();
                        }
                        cobolStatementInProgress = true;
                    }
                    appendTokenToCobolStatement(testSuiteToken);
                    break;
                case FIELDNAME:
                    if (cobolStatementInProgress) {
                        appendTokenToCobolStatement(testSuiteToken);
                    }
                    break;
            }
            nextAction = keyword.keywordAction();
            testSuiteToken = getNextTokenFromTestSuite(testSuiteReader);
        }
        if (cobolStatementInProgress) {
            insertUserWrittenCobolStatement(testSourceOut);
        }
    }

    /**
     * This method hides file I/O from the test suite parsing logic so the parsing logic will be easier to understand.
     * We don't want to load the whole test suite into memory at once, as we don't know how large it may be.
     * Here we consume tokens one by one and invoke the file read routine whenever we exhaust the list of tokens.
     * When the file read routine returns a null reference, it means we have reached end-of-file on the test suite.
     * This method uses a keyword extractor instance to get tokens from the input record. "Tokens" in this context
     * may mean phrases that contain embedded spaces, like "TO BE", and quoted string literals with the quotes intact.
     * Comment lines are bypassed, as there is no need to insert them into the test program.
     *
     * @param testSuiteReader - reader attached to the concatenated test suite files.
     * @return - the next token from the testSuiteReader.
     */
    private String getNextTokenFromTestSuite(BufferedReader testSuiteReader) {
        while (testSuiteTokens.isEmpty()) {
            String testSuiteLine = readNextLineFromTestSuite(testSuiteReader);
            if (testSuiteLine == null) {
                return null;
            }
            if (testSuiteLine.length() > 0 && !testSuiteLine.trim().startsWith("*")) {
//            if (testSuiteLine.length() > 5 && testSuiteLine.charAt(6) != '*') {
                testSuiteTokens = keywordExtractor.extractTokensFrom(testSuiteLine);
            }
        }
        String testSuiteToken = testSuiteTokens.get(0);
        testSuiteTokens.remove(0);
        return testSuiteToken;
    }

    /**
     * This method performs the grunt work of reading records from the test suite input source.
     *
     * @param testSuiteReader - reader attached to the concatenated test suite files.
     * @return - line of source from the testSuiteReader.
     */
    private String readNextLineFromTestSuite(BufferedReader testSuiteReader) {
        String testSuiteLine;
        try {
            testSuiteLine = testSuiteReader.readLine();
            if (testSuiteLine == null) {
                if (emptyTestSuite) {
                    throw new PossibleInternalLogicErrorException(messages.get("ERR010"));
                }
                return null;
            }
            emptyTestSuite = false;
            return testSuiteLine;
        } catch (IOException ioEx) {
            throw new TestSuiteCouldNotBeReadException(ioEx);
        } catch (Exception ex) {
            throw new PossibleInternalLogicErrorException(ex);
        }
    }

    // Helper methods to insert code into the test program being generated based on interpretation of user-written
    // test case code.

    void insertTestInitializationLineIntoTestSource(Writer testSourceOut) throws IOException {
        testSourceOut.write(fixedLength(String.format(COBOL_PERFORM_INITIALIZE, testCodePrefix)));
    }

    void insertTestSuiteNameIntoTestSource(String testSuiteName, Writer testSourceOut) throws IOException {
        testSourceOut.write(fixedLength(COBOL_DISPLAY_TESTSUITE));
        writeCobolLine(String.format(COBOL_DISPLAY_NAME, testSuiteName), testSourceOut);
    }

    void insertTestCaseNameIntoTestSource(String testCaseName, Writer testSourceOut) throws IOException {
        writeCobolLine(String.format(COBOL_STORE_TESTCASE_NAME_1, testCaseName), testSourceOut);
        testSourceOut.write(fixedLength(String.format(COBOL_STORE_TESTCASE_NAME_2, testCodePrefix)));
        testSourceOut.write(fixedLength(String.format(COBOL_PERFORM_BEFORE, testCodePrefix)));
    }

    void insertPerformBeforeEachIntoTestSource(Writer testSourceOut) throws IOException {
        testSourceOut.write(fixedLength(String.format(COBOL_PERFORM_BEFORE, testCodePrefix)));
    }

    void insertIncrementTestCaseCount(Writer testSourceOut) throws IOException {
        testSourceOut.write(fixedLength(String.format(COBOL_INCREMENT_TEST_CASE_COUNT, testCodePrefix)));
    }

    void insertTestCodeForAssertion(Writer testSourceOut, NumericFields numericFields) throws IOException {
        if (boolean88LevelCompare) {
            insertTestCodeFor88LevelEqualityCheck(testSourceOut);
        } else {
            insertSetNormalOrReverseCompare(testSourceOut);
            if (fieldIsANumericDataType(fieldNameForExpect)) {
                testSourceOut.write(fixedLength(String.format(
                        COBOL_SET_COMPARE_NUMERIC, testCodePrefix, Constants.TRUE)));
                testSourceOut.write(fixedLength(String.format(
                        COBOL_MOVE_FIELDNAME_TO_ACTUAL_NUMERIC, testCodePrefix, fieldNameForExpect)));
                testSourceOut.write(fixedLength(String.format(
                        COBOL_MOVE_EXPECTED_NUMERIC_LITERAL, testCodePrefix, expectedValueToCompare)));
            } else {
                testSourceOut.write(fixedLength(String.format(
                        COBOL_SET_ALPHANUMERIC_COMPARE, testCodePrefix, Constants.TRUE)));
                testSourceOut.write(fixedLength(String.format(
                        COBOL_MOVE_FIELDNAME_TO_ACTUAL, testCodePrefix, fieldNameForExpect)));
                String cobolLine = String.format(
                        COBOL_MOVE_EXPECTED_ALPHANUMERIC_LITERAL_1, expectedValueToCompare);
                writeCobolLine(cobolLine, testSourceOut);
                testSourceOut.write(fixedLength(String.format(
                        COBOL_MOVE_EXPECTED_ALPHANUMERIC_LITERAL_2, testCodePrefix)));
            }
            testSourceOut.write(fixedLength(String.format(
                    COBOL_SET_RELATION,
                    testCodePrefix,
                    greaterThanComparison ? RELATION_GT
                            : lessThanComparison ? RELATION_LT
                            : RELATION_EQ,
                    Constants.TRUE)));
            insertFinalLines(testSourceOut);
            greaterThanComparison = false;
            lessThanComparison = false;
        }
    }

    void insertTestCodeFor88LevelEqualityCheck(Writer testSourceOut) throws IOException {
        testSourceOut.write(fixedLength(String.format(
                COBOL_SET_COMPARE_88_LEVEL, testCodePrefix, Constants.TRUE)));
        testSourceOut.write(fixedLength(String.format(
                COBOL_SET_ACTUAL_88_VALUE_1, fieldNameForExpect)));
        testSourceOut.write(fixedLength(String.format(
                COBOL_SET_ACTUAL_88_VALUE_2, testCodePrefix)));
        testSourceOut.write(fixedLength(String.format(
                COBOL_SET_ACTUAL_88_VALUE_3, testCodePrefix)));
        testSourceOut.write(fixedLength(
                COBOL_SET_ACTUAL_88_VALUE_4));
        testSourceOut.write(fixedLength(String.format(
                COBOL_SET_ACTUAL_88_VALUE_5, testCodePrefix)));
        testSourceOut.write(fixedLength(String.format(
                COBOL_SET_ACTUAL_88_VALUE_6, testCodePrefix)));
        testSourceOut.write(fixedLength(
                COBOL_SET_ACTUAL_88_VALUE_7));
        if (reverseCompare) {
            if (expectedValueToCompare.equals(Constants.TRUE)) {
                expectedValueToCompare = Constants.FALSE;
            } else {
                expectedValueToCompare = Constants.TRUE;
            }
        }
        testSourceOut.write(fixedLength(String.format(
                COBOL_SET_EXPECTED_88_VALUE, testCodePrefix, expectedValueToCompare)));
        testSourceOut.write(fixedLength(String.format(
                COBOL_SET_EXPECTED_88_VALUE_1, testCodePrefix)));
        testSourceOut.write(fixedLength(String.format(
                COBOL_SET_EXPECTED_88_VALUE_2, testCodePrefix)));
        testSourceOut.write(fixedLength(
                COBOL_SET_EXPECTED_88_VALUE_3));
        testSourceOut.write(fixedLength(String.format(
                COBOL_SET_EXPECTED_88_VALUE_4, testCodePrefix)));
        testSourceOut.write(fixedLength(
                COBOL_SET_EXPECTED_88_VALUE_5));
        insertFinalLines(testSourceOut);
    }

    /**
     * Writes a Cobol source statement of the form SET XX-NORMAL-COMPARE TO TRUE or SET XX-REVERSE-COMPARE TO TRUE
     * depending on whether NOT was specified in an EXPECT specification.
     *
     * @param testSourceOut - Writer for the generated test program
     * @throws IOException - May be thrown by the write method
     */
    void insertSetNormalOrReverseCompare(Writer testSourceOut) throws IOException {
        testSourceOut.write(fixedLength(String.format(
                COBOL_SET_NORMAL_OR_REVERSE_COMPARE,
                testCodePrefix,
                reverseCompare ? REVERSE : NORMAL,
                Constants.TRUE)));
        reverseCompare = false;
    }

    /**
     * Writes the final lines of Cobol for a test case, common to different kinds of test cases.
     *
     * @param testSourceOut - Writer for the generated test program
     * @throws IOException - May be thrown by the write method
     */
    void insertFinalLines(Writer testSourceOut) throws IOException {
        testSourceOut.write(fixedLength(String.format(
                COBOL_CHECK_EXPECTATION, testCodePrefix)));
        testSourceOut.write(fixedLength(String.format(
                COBOL_PERFORM_AFTER, testCodePrefix)));
    }

    /**
     * Generator compiles a list of Data Division item names from the program under test that represent numeric
     * data types. This method returns true when the item name of the "actual" field in an EXPECT specification
     * is in that list.
     *
     * @param fieldNameForExpect - the field name of the "actual" reference in an EXPECT specification
     * @return true when the field name represents any numeric data type
     */
    boolean fieldIsANumericDataType(String fieldNameForExpect) {
        return numericFields.dataTypeOf(fieldNameForExpect) == DataType.PACKED_DECIMAL
                || (numericFields.dataTypeOf(fieldNameForExpect) == DataType.FLOATING_POINT)
                || (numericFields.dataTypeOf(fieldNameForExpect) == DataType.DISPLAY_NUMERIC);
    }

    /**
     * Build a Cobol statement out of tokens from the test suite input.
     * Users may code standard Cobol statements to set up preconditions for a test case.
     * These tokens may occur immediately following the test case name string.
     * Users may code standard Cobol statements to define the behavior of a MOCK.
     *
     * @param testSuiteToken - token extracted from test suit input
     */
    void appendTokenToCobolStatement(String testSuiteToken) {
        if (cobolStatement.length() > 0) cobolStatement.append(Constants.SPACE);
        cobolStatement.append(testSuiteToken);
    }

    /**
     * Insert user-written Cobol statement from a test suite (not from the program under test) into the test program
     * being generated.
     *
     * @param testSourceOut - writer attached to the test program being generated.
     * @throws IOException - pass any IOExceptions to the caller
     */
    void insertUserWrittenCobolStatement(Writer testSourceOut) throws IOException {
        testSourceOut.write(fixedLength(cobolStatement.toString()));
    }

    private void initializeCobolStatement() {
        cobolStatement = new StringBuffer(ELEVEN_LEADING_SPACES);
    }

    String getCurrentTestSuiteName() {
        return currentTestSuiteName;
    }

    String getCurrentTestCaseName() {
        return currentTestCaseName;
    }

    String getCobolStatement() {
        return cobolStatement.toString();
    }

    /**
     * Lines of test code in a test suite are Cobol-like, but not strictly Cobol. The descriptions for TESTSUITE and
     * TESTCASE specifications may exceed the maximum length allowed for Area B in the generated test Cobol program.
     * This method splits the literal and writes the value with a continuation line, if necessary.
     *
     * Limitation: Only works for a maximum of 2 source lines.
     *
     * @param line - original line from test suite.
     * @param testSourceOut - writer attached to the test program being generated.
     * @throws IOException - pass any IOExceptions to the caller.
     */
    void writeCobolLine(String line, Writer testSourceOut) throws IOException {
        //TODO: Enhance this to work with an arbitrary number of continuation lines
        String line1 = line;
        String line2 = Constants.EMPTY_STRING;
        if (line.length() > 72) {
            line1 = line.substring(0,72);
            line2 = line.substring(72);
        }
        testSourceOut.write(fixedLength(line1));
        if (line2.length() > 0) {
            line2 = fixedLength("      -    \"" + line2);
        }
        testSourceOut.write(line2);
    }


}

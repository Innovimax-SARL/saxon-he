using System;
using System.IO;
using System.Collections;
using System.Xml;
using Saxon.Api;

/**
  * Class XPathExample:
  * This class illustrates the use of the JAXP XPath API. It is a simple command-line application,
  * which prompts the user for a word, and replies with a list of all the lines containing that
  * word within a Shakespeare play.
  */

public class XPathExample {

    private String currentWord;

    /**
      * main()<BR>
      * Expects one argument, the input filename<BR>
      */

    public static void Main(String[] args) {
        // Check the command-line arguments

        if (args.Length != 1) {
            Console.WriteLine("Usage: XPathExample input-file");
            return;
        }
        XPathExample app = new XPathExample();
        app.go(args[0]);
    }

    /**
    * Run the application
    */

    public void go(String filename) {

        Processor processor = new Processor();
        XPathCompiler xpe = processor.NewXPathCompiler();

        // Build the source document. 

        DocumentBuilder builder = processor.NewDocumentBuilder();
        builder.BaseUri = new Uri(filename);
        builder.WhitespacePolicy = WhitespacePolicy.StripAll;
        XdmNode indoc = builder.Build(
                new FileStream(filename, FileMode.Open, FileAccess.Read));


        // Compile the XPath expressions used by the application

        QName wordName = new QName("", "", "word");
        xpe.DeclareVariable(wordName);

        XPathSelector findLine =
            xpe.Compile("//LINE[contains(., $word)]").Load();
        XPathSelector findLocation =
            xpe.Compile("concat(ancestor::ACT/TITLE, ' ', ancestor::SCENE/TITLE)").Load();
        XPathSelector findSpeaker =
            xpe.Compile("string(ancestor::SPEECH/SPEAKER[1])").Load();


        // Loop until the user enters "." to end the application

        while (true) {

            // Prompt for input
            Console.WriteLine("\n>>>> Enter a word to search for, or '.' to quit:\n");

            // Read the input
            String word = Console.ReadLine().Trim();
            if (word == ".") {
                break;
            }
            if (word != "") {

                // Set the value of the XPath variable
                currentWord = word;

                // Find the lines containing the requested word
                bool found = false;
                findLine.ContextItem = indoc;
                findLine.SetVariable(wordName, new XdmAtomicValue(word));
                foreach (XdmNode line in findLine) {

                    // Note that we have found at least one line
                    found = true;

                    // Find where it appears in the play
                    findLocation.ContextItem = line;
                    Console.WriteLine("\n" + findLocation.EvaluateSingle());

                    // Output the name of the speaker and the content of the line
                    findSpeaker.ContextItem = line;
                    Console.WriteLine(findSpeaker.EvaluateSingle() + ":  " + line.StringValue);

                }

                // If no lines were found, say so
                if (!found) {
                    Console.WriteLine("No lines were found containing the word '" + word + '\'');
                }
            }
        }

        // Finish when the user enters "."
        Console.WriteLine("Finished.");
    }

    /**
     * This class serves as a variable resolver. The only variable used is $word.
     * @param qName the name of the variable required
     * @return the current value of the variable
     */

    public Object resolveVariable(QName qName) {
        if (qName.LocalName == "word") {
            return currentWord;
        } else {
            return null;
        }
    }

}
using System;
using System.Collections.Generic;
using System.Windows.Forms;

namespace TestRunner
{
    static class TestRunnerProgram
    {
        /// <summary>
        /// The main entry point for the application.
        /// </summary>
        [STAThread]
        static void MainXX()
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new TestRunnerForm());
        }
    }
}

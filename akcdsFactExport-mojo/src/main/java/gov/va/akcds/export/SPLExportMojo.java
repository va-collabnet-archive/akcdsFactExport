package gov.va.akcds.export;

import java.io.File;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.ihtsdo.db.bdb.Bdb;
import org.ihtsdo.tk.Ts;

/**
 * Goal to generate spl data file
 * 
 * @goal export-spl
 * 
 * @phase process-sources
 */
public class SPLExportMojo extends AbstractMojo
{

	/**
	 * Where to write the output files
	 * 
	 * @parameter expression="${project.build.directory}"
	 * @required
	 */
	private File outputDirectory;

	/**
	 * Location of the workbench database to read
	 * 
	 * @parameter
	 * @required
	 */
	private File databasePath;

	/**
	 * Method used by maven to create the .jbin data file.
	 */
	public void execute() throws MojoExecutionException
	{
		try
		{
			System.out.println("Opening Database " + databasePath);

			Bdb.setup(databasePath.getAbsolutePath());

			System.out.println("Database Open");
			
			SPLConceptProcessor cp = new SPLConceptProcessor(outputDirectory);

			Ts.get().iterateConceptDataInSequence(cp);
			
			cp.shutdown();
		}
		catch (Exception e)
		{
			System.out.println("Failure during export: " + e);
			e.printStackTrace();
		}

	}

}

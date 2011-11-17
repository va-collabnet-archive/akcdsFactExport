package gov.va.akcds.export.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

public class RF2FileWriter
{
	private BufferedWriter writer_;
	private FileOutputStream fos_;
	private String eol_ = "\r\n";
	private String delimiter_ = "\t";
	
	public RF2FileWriter(String[] columnNames, File output, String description) throws IOException
	{
		output.getParentFile().mkdirs();
		ConsoleUtil.println("See: " + output + " for " + description);
		
		fos_ = new FileOutputStream(output);
		//Java has issues with UTF-8, requiring this manual bit of crud to get the BOM char written: http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4508058
		//Not sure if RF2 cares one way or another about the BOM.
		fos_.write(new byte[] {(byte)0xEF, (byte)0xBB, (byte)0xBF});
		writer_ = new BufferedWriter(new OutputStreamWriter(fos_, "UTF8"));

		
		for (int i = 0; i < columnNames.length; i++)
		{
			writer_.write(columnNames[i]);
			if (i < columnNames.length - 1)
			{
				writer_.write(delimiter_);
			}
		}
		writer_.write(eol_);
	}
	
	public void addLine(String[] columnData) throws IOException
	{
		for (int i = 0; i < columnData.length; i++)
		{
			writer_.write(columnData[i]);
			if (i < columnData.length - 1)
			{
				writer_.write(delimiter_);
			}
		}
		writer_.write(eol_);
	}
	
	public void close() throws IOException
	{
		writer_.flush();
		writer_.close();
		fos_.close();
	}
}

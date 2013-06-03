package com.niranjanrao;

import org.junit.Assert;
import org.junit.Test;

public class TestGirToGnomeJava {

	@Test
	public void testMethodNameMapping() {
		String methodName = "webkit_web_history_item_new_with_data";

		Assert.assertEquals("Not expected value", "WebHistoryItemWithData",
				GirToGnomeJava.mapToJavaMethodName(methodName));
	}
	
	@Test
	public void testName()
	{
		String matcher = "(const\\s+)?(g?)char\\s*\\*";
		
		String text = "gchar*";
		Assert.assertTrue(text, text.matches(matcher));
		
		text = "gchar *";
		Assert.assertTrue(text, text.matches(matcher));
		
		text = "char*";
		Assert.assertTrue(text, text.matches(matcher));
		
		text = "const char*";
		Assert.assertTrue(text, text.matches(matcher));
	}
}

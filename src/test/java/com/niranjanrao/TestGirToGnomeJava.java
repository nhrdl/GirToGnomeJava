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
}

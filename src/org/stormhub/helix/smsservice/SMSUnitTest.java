package org.stormhub.helix.smsservice;

import static org.junit.Assert.*;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for smsservice-lib 1.0.
 * 
 * @author Realiserad
 */
public class SMSUnitTest {
	private SMS sms;

	// TODO Fill in API key and test phone here
	private static final String API_KEY_NAME = "test";
	private static final String API_KEY_VALUE = "rmJmnjZigiMXzNKtulP2tu7YSt9ihVGbAd8VQ05tbQh";
	private static final String TEST_PHONE = "+46700634607";
	private static final String SPARE_PHONE = "";

	// Possible server responses
	private String success = "Message was sent.";
	private String failure = "Message was not sent.";

	// Invalid phone extensions
	String[] numbers = new String[] { 
			"00731234567", 
			"+4600731234567", 
			"761234567", 
			"+46301234567", 
			"0731234567", 
			"0371223458", 
			"+46 73 123 45 67", 
			"+4673-1234567",
	};

	@Before
	public void setUp() throws Exception {
		sms = SMS.create(API_KEY_NAME, API_KEY_VALUE);
	}

	/**
	 * Try to send an ordinary SMS sent once.
	 */
	@Test
	public void testOneRecipient() {
		assertSame(
				// Test UTF-8 charset
				sms.setMessage("едц/?=.-,!#%&\"+*^abc<>():").
				setRecipient(TEST_PHONE).
				send(),

				success
				);
	}

	/**
	 * Try to send an ordinary SMS twice.
	 */
	@Test
	public void testMultipleRecipients() {
		assertSame(	
				sms.setMessage("This message should be delivered to two devices.").
				setRecipient(new String[] { TEST_PHONE, SPARE_PHONE }).
				send(),

				success
				);
	}

	/**
	 * Try to send a flash message.
	 */
	@Test
	public void testFlash() {
		assertSame(	
				sms.setMessage("Flash!").
				setRecipient(TEST_PHONE).
				sendAsFlash(),

				success
				);
	}

	/**
	 * Test invalid phone extensions.
	 */
	@Test
	public void testExtensionFormats() {
		for (String number : numbers) {
			try {
				sms.setMessage("This message will not be delivered.").
				setRecipient(number);
				fail(number  + " should throw NumberFormatException.");
			} catch (NumberFormatException e) {
				assertTrue(true);
			}
		}
	}

	/**
	 * Test asynchronous sending.
	 */
	@Test
	public void testSendAsync() {
		sms.setMessage("This message was sent asynchronously.").
		setRecipient(TEST_PHONE).
		sendAsync(new Callback() {
			@Override
			public void onResponse(String response) {
				assertEquals(response, success);
			}
		});
	}

	/**
	 * Test to postpone an SMS.
	 */
	@Test
	public void testPostpone() {
		SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.MINUTE, 3); // postpone for three minutes
		assertEquals(
				sms.setMessage("This message was sent " + sdf.format(new Date()) + ".").
				setRecipient(TEST_PHONE).
				postpone(cal.getTime()).
				send(),
				
				success
				);
	}
	
	/**
	 * Test invalid API key.
	 */
	@Test
	public void testSecurity() {
		assertEquals(
				SMS.create("foo32", "0x0003b").
				setMessage("This message should not be delivered.").
				setRecipient(TEST_PHONE).
				send(),
				
				failure
				);
	}
}

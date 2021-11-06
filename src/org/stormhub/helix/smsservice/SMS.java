package org.stormhub.helix.smsservice;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;

/**
 * smsservice-lib is a Java backend for smsservice available at helix.stormhub.org/smsservice
 * 
 * This web service lets you send SMS messages from any device connected to the internet!
 * In order to send SMS messages you'll need an API key.
 * 
 * For further information about the service and to download the latest version of this library, 
 * please consult the website mentioned above or send an email to bastianf@kth.se
 * 
 * _____________________________________
 * |                                   |
 * |  smsservice-lib version history   |
 * |                                   |
 * |___________________________________|
 * ______|
 * |     |
 * | 1.0 |
 * |_____|
 * First version of smsservice-lib. Supporting multiple recipients, format control, 
 * postpone sending, send as flash message and asynchronous sending with code injection. 
 * Incorporates the StartCom certificate, unit tests and Apache HTTPClient.
 * 
 * @author Realiserad
 * @version 1.0
 */
public class SMS {
	private static final String BASE_URL = "https://helix.stormhub.org/smsservice/sendsms.php";
	private String API_KEY_NAME;
	private String API_KEY_VALUE;
	private String message = "";
	private ArrayList<String> recipients = new ArrayList<String>();
	private boolean asFlash;
	private Date postpone;
	private static final String GSM_EXTENSION_FORMAT = "\\+\\d{2}7\\d{8}";
	private static SSLContext sslContext;

	private SMS(String API_KEY_NAME, String API_KEY_VALUE) {
		/* Save the credentials to the SMS gateway. */
		this.API_KEY_NAME = API_KEY_NAME;
		this.API_KEY_VALUE = API_KEY_VALUE;
		
		/* The StartCom certificate authority is not in Oracles default KeyStore, thus we need to 
		 * import the StartCom certificates to our custom TrustStore and use this when we connect 
		 * to Stormhub. This issue does not seem to occur when one is using OpenJDK.
		 */
		if (sslContext == null) {
			try {
				// Read the certificate bundle from disk
				CertificateFactory cf = CertificateFactory.getInstance("X.509");
				Certificate ca = cf.generateCertificate(getClass().getResourceAsStream("ca-bundle.crt"));
				// Create a KeyStore containing our trusted CAs
				KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
				keyStore.load(null, null);
				keyStore.setCertificateEntry("ca", ca);
				// Create a TrustStore that trusts the CAs in our KeyStore
				TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
				tmf.init(keyStore);
				// Create an SSLContext that uses our TrustManager
				sslContext = SSLContext.getInstance("TLS");
				sslContext.init(null, tmf.getTrustManagers(), null);
			} catch (CertificateException | KeyStoreException | NoSuchAlgorithmException | KeyManagementException | IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Create a new empty SMS without recipient. Before sending the SMS
	 * you should</br>
	 * 1) Set the SMS content using setMessage()</br>
	 * 2) Set one or more recipients using setRecipient()</br>
	 * Send the SMS by invoking send() on the SMS object.
	 * @param API_KEY_NAME The name of the API key used for authentication with the SMS gateway
	 * @param API_KEY_VALUE The API key itself corresponding to the API_KEY_NAME used
	 * @return An SMS to be sent
	 */
	public static SMS create(String API_KEY_NAME, String API_KEY_VALUE) {
		return new SMS(API_KEY_NAME, API_KEY_VALUE);
	}

	/**
	 * Set the recipient to one single extension on the format +CC7XXXXXXXX
	 * where CC is the two digit country code and 7XXXXXXXX is the phone number
	 * of the person who should receive the SMS.
	 * @param recipient The extension of the recipient
	 * @return An SMS to be sent
	 * @throw NumberFormatException If the extension is not on the format +CC7XXXXXXXX
	 */
	public SMS setRecipient(String recipient) throws NumberFormatException {
		// Check format
		if (!recipient.matches(GSM_EXTENSION_FORMAT)) {
			throw new NumberFormatException("The phone number " + recipient + 
					" does not match the format " + GSM_EXTENSION_FORMAT);
		}
		this.recipients.add(recipient);
		return this;
	}

	/**
	 * Set multiple recipients defined by an array of phone numbers. Each phone number must be
	 * on the format +CC7XXXXXXXX where CC is the two digit country code and 7XXXXXXXX is the 
	 * phone number of the person who should receive the SMS.
	 * @param recipients An array of phone numbers
	 * @return An SMS to be sent
	 * @throw NumberFormatException If one or more of the extensions are not on the format +CC7XXXXXXXX
	 */
	public SMS setRecipient(String[] recipients) throws NumberFormatException {
		for (int i = 0; i < recipients.length; i++) {
			setRecipient(recipients[i]);
		}
		return this;
	}

	/**
	 * Set the content of the SMS, e.g "Hi! What's up?"
	 * @param message The content of the message, not longer than 500 characters
	 * @return An SMS to be sent
	 */
	public SMS setMessage(String message) {
		this.message = message;
		return this;
	}

	/**
	 * Defer sending of this SMS until the time and date specified.
	 * An example how to postpone the SMS for two hours:</br>
	 * </br>
	 * {@code
	 * 		SMS sms = SMS.create(API_KEY_NAME, API_KEY_VALUE);</br>
	 *  	Calendar calendar = Calendar.getInstance();</br>
	 * 		calendar.add(Calendar.HOUR, 2);</br>
	 * 		sms.postpone(calendar.getTime());</br>
	 * }
	 * @return An SMS to be sent
	 */
	public SMS postpone(Date date) {
		this.postpone = date;
		return this;
	}
	
	/**
	 * Send the SMS asynchronously. 
	 * @param callback A class containing code which will be executed after the SMS has been sent
	 */
	public void sendAsync(final Callback callback) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				callback.onResponse(send());
			}	
		}).start();
	}
	
	/**
	 * Send this SMS as a flash message asynchronously.
	 * @param callback A class containing code which will be executed after the SMS has been sent
	 */
	public void sendAsFlashAsync(final Callback callback) {
		asFlash = true;
		sendAsync(callback);
	}
	
	/**
	 * Send this SMS as a flash message.
	 * @param The response from server or null if communication failed
	 */
	public String sendAsFlash() {
		asFlash = true;
		return send();
	}

	/**
	 * Send the SMS.
	 * @return The response sent from server or null if communication failed
	 */
	public String send() {
		try {
			CloseableHttpClient client = HttpClients.custom().setSslcontext(sslContext).build();
			HttpPost post = new HttpPost(BASE_URL);
			List<NameValuePair> urlParameters = new ArrayList<NameValuePair>();
			// Message
			urlParameters.add(new BasicNameValuePair("message", message));
			// Recipients
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < recipients.size(); i++) {
				sb.append(recipients.get(i));
				if (i + 1 < recipients.size()) {
					sb.append(",");
				}
			}
			urlParameters.add(new BasicNameValuePair("extension", sb.toString()));
			// Credentials
			urlParameters.add(new BasicNameValuePair("key_name", API_KEY_NAME));
			urlParameters.add(new BasicNameValuePair("key_value", API_KEY_VALUE));
			// Send as flash?
			if (asFlash) urlParameters.add(new BasicNameValuePair("flash", ""));
			// Postpone?
			if (postpone != null) {
				SimpleDateFormat sdf = new SimpleDateFormat("HH:mm MMddyy");
				sdf.setTimeZone(TimeZone.getTimeZone("GMT+2")); // calibrate to server time
				urlParameters.add(new BasicNameValuePair("postpone", sdf.format(postpone)));
			}
			
			post.setEntity(new UrlEncodedFormEntity(urlParameters, "UTF-8"));
			HttpResponse response = client.execute(post);
			BufferedReader buf = new BufferedReader(
					new InputStreamReader(response.getEntity().getContent()));
			StringBuffer result = new StringBuffer();
			String line = "";
			while ((line = buf.readLine()) != null) {
				result.append(line);
			}
			return result.toString();
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
}
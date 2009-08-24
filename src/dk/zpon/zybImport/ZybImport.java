/*
 * ZybImport - zyb importer for android
 * Copyright (C) 2009 Søren Juul <zpon.dk at gmail.com>
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package dk.zpon.zybImport;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Contacts;
import android.provider.Contacts.ContactMethodsColumns;
import android.provider.Contacts.OrganizationColumns;
import android.provider.Contacts.PeopleColumns;
import android.provider.Contacts.PhonesColumns;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class ZybImport extends ListActivity {

	private TextView mTextview;
	private JSONArray mContacts;
	private Button mStart;
	private Button mStop;
	private CheckAdapter mListAdapter;
	private AsyncTask<Object, ViewHolder, Void> mImportContacts;
	private int mTotalToImport;
	private CookieStore mCookies;

	private boolean mLoginSuccess = false;
	private Context mContext;
	private ProgressDialog mLoginDialog;
	private int messageCount = 0;
	private ProgressDialog mProgressDialog;
	protected String mProfilepath;
	private boolean mImportPicture;
	public byte[] mUnspecifiedImage; // Cache of unspecified image (/images/avatars/unspecified_100x100.gif)

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		mContext = this;

		this.mTextview = (TextView)this.findViewById(R.id.textview);
		this.mTextview.setText("Please wait...");
		this.mStop = (Button) findViewById(R.id.stop);
		this.mStart = (Button) findViewById(R.id.start);

		showDialog(1);

		mStart.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				// reactivate "cancel" button and deactivate "start" button
				mStop.setEnabled(true);
				mStop.setClickable(true);
				mStart.setEnabled(false);
				mStart.setClickable(false);
				mListAdapter.enableItems(false);
				mImportContacts = new ImportContacts();
				mImportContacts.execute();
				mTextview.setText("Importing, please wait");
			}
		});

		mStop.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				mImportContacts.cancel(true);
				// Deactivate "cancel" button and reactivate "start" button
				mStop.setEnabled(false);
				mStop.setClickable(false);
				mStart.setText("Restart");
				mStart.setEnabled(true);
				mStart.setClickable(true);
				mListAdapter.enableItems(true);
			}
		});
	}

	protected Dialog onCreateDialog(int id)
	{    
		final Dialog dialog = new Dialog(this);
		dialog.setContentView(R.layout.login);
		dialog.setTitle("Login");

		((TextView)dialog.findViewById(R.id.text)).setText("Enter you zyb.com login data and click connect.");

		((Button)dialog.findViewById(R.id.connect)).setOnClickListener(new View.OnClickListener(){
			public void onClick(View v) {

				new Thread(){
					public void run()
					{
						// Should pictures be imported?
						if(((CheckBox)dialog.findViewById(R.id.pictures)).isChecked())
							mImportPicture = true;
						else
							mImportPicture = false;

						/* wget --save-cookies cookies.txt \
						 * 		--keep-session-cookies \
						 * 		--post-data '__VIEWSTATE=&ctl00%24ContentPlaceHolder1%24txtUsername=username&ctl00%24ContentPlaceHolder1%24txtPassword=password&ctl00%24ContentPlaceHolder1%24btnSave=Log+in' \
						 * 		https://zyb.com/login/default.aspx
						 */
						// http://svn.apache.org/repos/asf/httpcomponents/httpclient/trunk/httpclient/src/examples/org/apache/http/examples/client/ClientFormLogin.java
						DefaultHttpClient httpclient = new DefaultHttpClient();

						HttpPost httpost = new HttpPost("https://zyb.com/login/default.aspx");
						List <NameValuePair> nvps = new ArrayList <NameValuePair>();
						nvps.add(new BasicNameValuePair("__VIEWSTATE", ""));
						nvps.add(new BasicNameValuePair("ctl00$ContentPlaceHolder1$txtUsername", ((EditText)dialog.findViewById(R.id.user)).getText().toString()));
						nvps.add(new BasicNameValuePair("ctl00$ContentPlaceHolder1$txtPassword", ((EditText)dialog.findViewById(R.id.pass)).getText().toString()));
						nvps.add(new BasicNameValuePair("ctl00$ContentPlaceHolder1$btnSave", "Log in"));

						try {
							httpost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));

							HttpResponse response = httpclient.execute(httpost);

							HttpEntity entity = response.getEntity();

							boolean error = false;
							boolean hasCookie = false;

							if(response.getStatusLine().getStatusCode() != 200)
								error = true;

							List<Cookie> cookieList = httpclient.getCookieStore().getCookies();
							mCookies = httpclient.getCookieStore();

							if (cookieList.isEmpty()) {
								error = true;
							}

							if(error)
								mLoginSuccess = false;
							else if (!cookieList.isEmpty()){
								for (int i = 0; i < cookieList.size(); i++) {
									if(cookieList.get(i).getName().equalsIgnoreCase("zyblogin"))
										hasCookie = true;
								}
							}
							if(!hasCookie)
								mLoginSuccess = false;
							else
							{
								// Find profilepath
								int bytesRead=0;
								int bytesToRead=(int)entity.getContentLength();
								if(bytesToRead != 0)
								{
									InputStream in = entity.getContent();
									byte[] input = new byte[bytesToRead];
									while (bytesRead < bytesToRead) {
										int result = in.read(input, bytesRead, bytesToRead - bytesRead);
										if (result == -1) break;
										bytesRead += result;
									}
									String sin = new String(input);
									int start = sin.indexOf("user_profilepath")+"user_profilepath = '/".length();
									int stop = -1;

									if(start != -1)
										stop = sin.substring(start).indexOf("'");

									if(start != -1 && stop != -1)
									{
										mProfilepath = sin.substring(start, start+stop);
										dialog.dismiss();
										mLoginSuccess = true;
									}
									else
										mLoginSuccess = false;
								}
							}
							sleep(5000); 
						} catch (HttpHostConnectException e) {
							mLoginSuccess = false;
							e.printStackTrace();
						} catch (UnknownHostException e) {
							mLoginSuccess = false;
							e.printStackTrace();
						} catch (UnsupportedEncodingException e) {
							mLoginSuccess = false;
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (ClientProtocolException e) {
							mLoginSuccess = false;
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (IOException e) {
							mLoginSuccess = false;
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (Exception e) {  } 

						messageCount = 1;

						handler.sendEmptyMessage(0);
					}
				}.start();

				mLoginDialog = ProgressDialog.show(v.getContext(), "Logging in", "Please wait ...", true);
			}
		});
		dialog.setCancelable(false);

		return dialog;
	}

	private void listAdapter()
	{
		setListAdapter(new CheckAdapter(this, mContacts));
		mListAdapter = (CheckAdapter)this.getListAdapter();

		mStart.setEnabled(true);
		mStart.setClickable(true);
	}

	private Handler handler = new Handler()
	{
		@Override
		public void handleMessage(Message msg) 
		{
			if(messageCount == 1)
			{
				if(mLoginSuccess)
				{
					mLoginDialog.dismiss();
					Toast.makeText(mContext, "Login was successful, the contacts will be loaded", Toast.LENGTH_LONG).show();
					
					mProgressDialog = ProgressDialog.show(mContext, "Please wait...", "Retrieving data ...", true);
					// Setup "Please wait" dialog
					Runnable getContacts = new Runnable()
					{
						@Override
						public void run() {
							loadFile();
						}
					};
					
					Thread thread =  new Thread(null, getContacts, "Load contacts");
					thread.start();
					
				}
				else
				{
					Toast.makeText(mContext, "Login was not successful, check your network connection and username and passwrod", Toast.LENGTH_LONG).show();
					mLoginDialog.dismiss();
				}
			}
			else if(messageCount == 2)
			{
				mProgressDialog.dismiss();
				if(mContacts != null)
				{
					int length = mContacts.length(); 

					mTextview.setText("Total of " + Integer.toString(length) + " contacts  to import");
					mTotalToImport = length;
					listAdapter();
				}
			}
		}
	};

	void loadFile()
	{
		try {
			DefaultHttpClient httpclient = new DefaultHttpClient();
			HttpPost httpost = new HttpPost("http://feeds.zyb.com/"+this.mProfilepath+"/contacts.json");
			httpclient.setCookieStore(this.mCookies);

			HttpResponse response = httpclient.execute(httpost);
			HttpEntity entity = response.getEntity();

			if(entity.getContentLength() != 0)
			{
				BufferedReader reader = new BufferedReader(new InputStreamReader(entity.getContent()));
				StringBuilder sb = new StringBuilder();
				String line;
				while ((line = reader.readLine()) != null) {
					sb.append(line + "\n");
				}
				String jsons = sb.toString();
				mContacts = new JSONArray(jsons);
			}

		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		messageCount = 2;
		handler.sendEmptyMessage(0);
	}

	private class ImportContacts extends AsyncTask<Object, ViewHolder, Void>
	{
		private static final String NODATE = "-62135596800000";
		private boolean cancelled;

		@Override
		protected Void doInBackground(Object... object)
		{
			cancelled = false;

			for(int i = 0; i < mListAdapter.getCount(); i++)
			{
				ViewHolder holder = (ViewHolder)mListAdapter.getItem(i);
				if(!cancelled && holder.getChecked() && !holder.isImported())
				{
					insertContact(holder.getData());
					publishProgress(holder);
				}
				else if(!holder.getChecked())
					publishProgress(holder);
				else if(cancelled)
					i = mListAdapter.getCount();
			}

			return null;
		}

		private void insertContact(JSONObject person)
		{
			try {
				int length;
				JSONObject vcard = person.getJSONObject("VCard");

				/*
				 * Create contact
				 */
				ContentValues personValues = new ContentValues();
				personValues.put(PeopleColumns.NAME, person.getString("Name"));
				// Save contact
				Uri newPerson = Contacts.People.createPersonInMyContactsGroup(getContentResolver(), personValues);

				if(newPerson != null)
				{
					/*
					 * Notes (including birthday, as contacts has no field for birthdays - http://code.google.com/p/android/issues/detail?id=1211)
					 * Only use one note - http://code.google.com/p/android/issues/detail?id=1165
					 */
					String snotes = "";
					if(!vcard.isNull("Birthday"))
					{
						String birthdate = vcard.getString("Birthday");
						String birthday = birthdate.substring(6, birthdate.lastIndexOf(")"));
						if(!birthday.equals(NODATE))
						{
							Long ldate = Long.valueOf(birthday);
							Date date = new Date(ldate);
							snotes = "Birthday: " + Integer.toString(date.getDate()) + "/" + Integer.toString((date.getMonth()+1)) + " " + Integer.toString(date.getYear()+1900) + "\n";
						}
					}
					if(!vcard.isNull("Notes"))
					{
						JSONArray notes = vcard.getJSONArray("Notes");
						length = notes.length();

						for(int i = 0; i < length; i++)
						{
							JSONObject note = notes.getJSONObject(i);
							snotes += note.getString("Value") + (i == length-1 ? "" : "\n");
						}
					}
					if(snotes.length() != 0)
					{
						personValues.clear();
						personValues.put(PeopleColumns.NOTES, snotes);
						getContentResolver().update(newPerson, personValues, null, null);
					}

					/*
					 * Image
					 */
					if(!person.isNull("Image") && mImportPicture)
					{
						// Import
						if((person.getString("Image").equals("/images/avatars/unspecified_100x100.gif") && mUnspecifiedImage == null) || !person.getString("Image").equals("/images/avatars/unspecified_100x100.gif"))
						{
							DefaultHttpClient httpclient = new DefaultHttpClient();
							HttpGet httpget = new HttpGet("http://zyb.com/"+person.getString("Image"));
							httpclient.setCookieStore(mCookies);

							HttpResponse response;
							try {
								response = httpclient.execute(httpget);
								HttpEntity entity = response.getEntity();

								// setPhotoData only allows up byte-arrays, and these can only be Integer.MAX_VALUE, which is (2^31)-1
								// If contentLength is unknown, we will also ommit
								if(Integer.MAX_VALUE >= entity.getContentLength() && entity.getContentLength() > 0)
								{
									int bytesRead=0;

									int bytesToRead=(int) entity.getContentLength();
									if(entity.getContentLength() != 0)
									{
										InputStream in;
										in = entity.getContent();
										byte[] input = new byte[bytesToRead];
										while (bytesRead < bytesToRead) {
											int result;
											result = in.read(input, bytesRead, bytesToRead - bytesRead);
											if (result == -1) break;
											bytesRead += result;
										}

										Contacts.People.setPhotoData(getContentResolver(), newPerson, input);

										if(person.getString("Image").equals("/images/avatars/unspecified_100x100.gif"))
											mUnspecifiedImage = input;
									}
								}
								else if(mUnspecifiedImage != null)
									Contacts.People.setPhotoData(getContentResolver(), newPerson, mUnspecifiedImage);

							} catch (ClientProtocolException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							} catch (IllegalStateException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
						// Use cache
						else if(person.getString("Image").equals("/images/avatars/unspecified_100x100.gif") && mUnspecifiedImage != null)
						{
							Contacts.People.setPhotoData(getContentResolver(), newPerson, mUnspecifiedImage);
							Log.w("----------------------", "smart");
						}
					}

					/*
					 * Organisation
					 */
					if(!vcard.isNull("Organization"))
					{
						String organization = vcard.getString("Organization");
						personValues.clear();
						Uri organizationUri = Uri.withAppendedPath(newPerson, Contacts.Organizations.CONTENT_DIRECTORY);
						personValues.put(OrganizationColumns.COMPANY, organization);
						personValues.put(OrganizationColumns.TYPE, OrganizationColumns.TYPE_OTHER);
						getContentResolver().insert(organizationUri, personValues);
					}

					/*
					 * Phone numbers
					 */
					if(!vcard.isNull("PhoneNumbers"))
					{
						JSONArray phoneNumbers = vcard.getJSONArray("PhoneNumbers");
						length = phoneNumbers.length();

						for(int i = 0; i < length; i++)
						{
							JSONObject phone = phoneNumbers.getJSONObject(i);

							personValues.clear();
							Uri mobileUri = Uri.withAppendedPath(newPerson, Contacts.People.Phones.CONTENT_DIRECTORY);

							personValues.put(PhonesColumns.NUMBER, phone.getString("Value"));
							personValues.put(PhonesColumns.TYPE, PhonesColumns.TYPE_OTHER);

							getContentResolver().insert(mobileUri, personValues);
						}
					}

					/*
					 * Email
					 */
					if(!vcard.isNull("Emails"))
					{
						JSONArray emails = vcard.getJSONArray("Emails");
						length = emails.length();

						for(int i = 0; i < length; i++)
						{
							JSONObject email = emails.getJSONObject(i);
							Uri emailUri = Uri.withAppendedPath(newPerson, Contacts.People.ContactMethods.CONTENT_DIRECTORY);
							personValues.clear();
							personValues.put(ContactMethodsColumns.KIND, Contacts.KIND_EMAIL);
							personValues.put(ContactMethodsColumns.TYPE, ContactMethodsColumns.TYPE_OTHER);
							personValues.put(ContactMethodsColumns.DATA, email.getString("Value"));
							getContentResolver().insert(emailUri, personValues);
						}
					}

					/*
					 * Address
					 */
					if(!vcard.isNull("Addresses"))
					{
						JSONArray addresses = vcard.getJSONArray("Addresses");
						length = addresses.length();

						for(int i = 0; i < length; i++)
						{
							JSONObject address = addresses.getJSONObject(i);
							Uri addressUri = Uri.withAppendedPath(newPerson, Contacts.People.ContactMethods.CONTENT_DIRECTORY);
							personValues.clear();
							personValues.put(ContactMethodsColumns.KIND, Contacts.KIND_POSTAL);
							personValues.put(ContactMethodsColumns.TYPE, ContactMethodsColumns.TYPE_OTHER);
							JSONObject values = address.getJSONObject("Value");
							personValues.put(ContactMethodsColumns.DATA, 
									(!values.isNull("StreetAddress") 	? values.getString("StreetAddress") + " " : "") +
									(!values.isNull("PostCode") 		? values.getString("PostCode") 		+ " " : "") +
									(!values.isNull("City") 			? values.getString("City") 			+ " " : "") + 
									(!values.isNull("Region") 			? values.getString("Region") 		+ " " : "") +  
									(!values.isNull("POBox") 			? values.getString("POBox") 		+ " " : "") + 
									(!values.isNull("Country") 			? values.getString("Country") : ""));
							getContentResolver().insert(addressUri, personValues);
						}
					}
				}
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		@Override
		protected void onProgressUpdate(ViewHolder... holders)
		{
			holders[0].setImported();
			/*if(holders[0].getImage() != null)
    			mImageView.setImageDrawable(holders[0].getImage());
			 */
			mTextview.setText("Importing, please wait ("+Integer.toString(holders[0].getId())+"/" + Integer.toString(mTotalToImport)+")");
		}

		@Override
		protected void onCancelled()
		{
			cancelled = true;
			mTextview.setText("Stopped");
		}

		@Override
		protected void onPostExecute(Void v)
		{
			if(!cancelled)
			{
				mTextview.setText("Done");
				mStop.setEnabled(false);
				mStop.setClickable(false);
			}
			else
				mTextview.setText("Stopped");
		}
	}
}

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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;

class CheckAdapter extends BaseAdapter {
	private LayoutInflater mInflater;
	private JSONArray mData; 
	private ViewHolder[] mViews;

	public CheckAdapter(Context context, JSONArray DATA) {
		mInflater = LayoutInflater.from(context);
		this.mData = DATA;
		this.mViews = new ViewHolder[this.mData.length()];
	}

	public void enableItems(boolean b)
	{
		ViewHolder holder;
		for(int i = 0; i < this.mData.length(); i++)
		{
			holder = (ViewHolder)this.getItem(i);
			holder.getCheckBox().setClickable(b);
		}
	}

	public int getCount() {
		return mData.length();
	}

	public Object getItem(int position) 
	{

		ViewHolder holder = null;

		if(this.mViews[position] == null)
		{
			View convertView = this.getView(position, null, null);
			holder = (ViewHolder) convertView.getTag();
		}
		else
			holder = this.mViews[position];

		return holder;
	}

	public long getItemId(int position) {
		return position;
	}

	public View getView(int position, View convertView, ViewGroup parent)
	{
		ViewHolder holder;

		if(this.mViews[position] != null)
		{
			holder = this.mViews[position];
			convertView = holder.getView();
		}
		else
		{
			convertView = mInflater.inflate(R.layout.contact, null);

			try {
				holder = new ViewHolder((CheckBox)convertView.findViewById(R.id.checkbox), position, (JSONObject)mData.get(position), convertView);
				convertView.setTag(holder);

				this.mViews[position] = holder;
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return convertView;
	}
}

class ViewHolder {
	CheckBox mCheckbox;
	int mId;
	JSONObject mData;
	View mView;
	boolean mIsImported;

	public ViewHolder(CheckBox checkbox, int id, JSONObject data, View view)
	{
		this.mCheckbox = checkbox;
		this.mCheckbox.setTag(this);
		this.mId = id;
		this.mData = data;
		try {
			this.mCheckbox.setText(mData.getString("Name"));
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		this.mView = view;
		this.mIsImported = false;
	}

	public JSONObject getData()
	{
		return this.mData;
	}

	public int getId()
	{
		return this.mId;
	}

	public boolean getChecked()
	{
		return this.mCheckbox.isChecked();
	}

	public void setChecked(boolean b)
	{
		this.mCheckbox.setChecked(b);
	}

	public CheckBox getCheckBox()
	{
		return this.mCheckbox;
	}

	public View getView()
	{
		return this.mView;
	}

	public boolean isImported()
	{
		return this.mIsImported;
	}

	public void setImported()
	{
		this.mIsImported = true;
		this.mCheckbox.setEnabled(false);
	}
}

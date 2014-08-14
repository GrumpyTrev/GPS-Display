package tvs.example.serviceprototype;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Html;
import android.text.util.Linkify;
import android.widget.TextView;

public class AboutDialogue extends Dialog
{
	public AboutDialogue (Context context ) 
	{
		super( context );
	}

	@Override
	public void onCreate( Bundle savedInstanceState )
	{
		setContentView(R.layout.about);
		
		TextView tv = ( TextView )findViewById( R.id.legal_text );
		tv.setText( Html.fromHtml( readRawTextFile( R.raw.legal ) ) );

		tv = ( TextView )findViewById( R.id.info_text );
		tv.setText( Html.fromHtml( readRawTextFile( R.raw.info ) ) );
		tv.setLinkTextColor( Color.WHITE );

		Linkify.addLinks(tv, Linkify.ALL);
	}
	
	private String readRawTextFile( int id ) 
	{
		InputStream inputStream = this.getContext().getResources().openRawResource( id );

		InputStreamReader in = new InputStreamReader( inputStream );
		BufferedReader buf = new BufferedReader( in );
		
		StringBuilder text = new StringBuilder();
		try 
		{
			String line = "";
			while ( ( line = buf.readLine() ) != null ) 
			{
				text.append( line );
			}
		} 
		catch ( IOException e ) 
		{
			return "";
		}

		return text.toString();
	}

}

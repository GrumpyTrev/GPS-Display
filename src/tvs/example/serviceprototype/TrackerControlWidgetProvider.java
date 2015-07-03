package tvs.example.serviceprototype;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tvs.example.serviceprototype.GPSInterfaceService.TrackingState;
import tvs.example.serviceprototype.TrackingStatus;
import uk.me.jstott.jcoord.LatLng;
import uk.me.jstott.jcoord.OSRef;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.location.Location;
import android.widget.RemoteViews;

public class TrackerControlWidgetProvider extends AppWidgetProvider
{
	 public TrackerControlWidgetProvider()
	 {
		 	serviceReceiver.SetStatusReceiver(
				new TrackerServiceManager.IStatusReceiver()
				{
					@Override
					public void onStatusReceived( TrackingStatus status, Context context ) 
					{
						receivedStatus = status;
						updateWidget( context );
					}
				} );
		 	
		 	serviceReceiver.SetLocationReceiver(
				new TrackerServiceManager.ILocationReceiver()
				{
					@Override
					public void onLocationReceived( Location location, Context context ) 
					{
						 receivedLocation = location;
						updateWidget( context );
					}
				} );

	 }
	   
	 @Override
	 public void onEnabled(Context context)
	 {
		 log.info( "onEnabled()" );
		 
	     super.onEnabled( context);
	 }

	 @Override
	 public void onDisabled(Context context)
	 {
		 log.info( "onDisabled()" );
		
		 super.onDisabled( context );
	 }

	 @Override
	 public void onUpdate( Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds )
	 {
		 log.info( "onUpdate()" );
		 
		 RemoteViews remoteView = buildLayout( context );
	    
	     for ( int widgetIndex = 0; widgetIndex < appWidgetIds.length; widgetIndex++ )
	     {
	    	 appWidgetManager.updateAppWidget( appWidgetIds[ widgetIndex ], remoteView );
	     }
	     
	     super.onUpdate( context, appWidgetManager, appWidgetIds );
	 }

	 @Override
	 public void onReceive( Context context, Intent intent )
	 {
		 String action = intent.getAction();
		 
		 log.info( "onReceive() action {}", action );
		 
		 if ( action == CONTROL_BUTTON_CLICKED )
		 {
			 // Launch the TrackerControlDialogue
			 Intent controlIntent = new Intent( context, TrackerControlDialogue.class );
			 controlIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			 context.startActivity( controlIntent );
			 
			 updateWidget( context );
		 }
		 else
		 {
			 serviceReceiver.onReceive( context, intent );
		 }

		 super.onReceive( context, intent );
	   }

	 private void updateWidget( Context contextForWidget )
	 {
		 RemoteViews remoteView = buildLayout( contextForWidget );

		 AppWidgetManager.getInstance( contextForWidget ).updateAppWidget( 
				 new ComponentName (contextForWidget, TrackerControlWidgetProvider.class), remoteView );
	 }
	 
	 
	 /**
	  * Build a RemoteViews with all the required intents and text settings
	  * @param context - The Context within which the provider is running
	  * @return A RemoteViews instance that can be used to update the widget
	  */
	 private RemoteViews buildLayout( Context context )
	 {
		 // Create a RemoteViews instance for this view and setup the click handler
		 RemoteViews remoteView = new RemoteViews( context.getPackageName(), R.layout.tracker_control_appwidget );

		 remoteView.setOnClickPendingIntent ( R.id.widget_layout, 
				 PendingIntent.getBroadcast( context, 0, 
						 new Intent( context, TrackerControlWidgetProvider.class ).setAction( CONTROL_BUTTON_CLICKED ), 0 ) );

		 // If the current location is known then set the location strings
		 String locationString = "";
		 String accuracyString = "";
		 String refString = "";
		 String utmString = "";
		 String altString = "";
		 
		 if ( receivedLocation != null )
		 {
			 locationString = String.format(  "Lat %f%nLon %f%n",  receivedLocation.getLatitude(), receivedLocation.getLongitude() );
			 utmString = String.format( "%n%s", new CoordinateConversion().latLon2UTM( receivedLocation.getLatitude(), receivedLocation.getLongitude() ) );
			 altString =  String.format( " alt %.0f", receivedLocation.getAltitude() );
			 
			 // Convert to GB OS
			 LatLng receivedLatLong = new LatLng( receivedLocation.getLatitude(), receivedLocation.getLongitude() );
			 receivedLatLong.toOSGB36();
			 refString = toEightFigureString( receivedLatLong.toOSRef() );
			 
			 accuracyString = String.format( "%2.0fm", receivedLocation.getAccuracy() );
		 }

		 // If the current status is known then set the status strings
		 String gpsString = "GPS off";
		 String statusString = "";

		 if ( receivedStatus != null )
		 {
			 statusString = String.format(  "view %d fix %d%s", receivedStatus.satellitesInView, receivedStatus.satellitesInFix, altString );
			 
			 switch ( receivedStatus.providerStatus )
			 {
				 case Disabled:
				 {
					 remoteView.setImageViewResource (R.id.gps_indicator_view, R.drawable.gps_status_indicator_red );
					 break;
				 }
				 
				 case NoLock:
				 {
					 gpsString = "No lock";
					 remoteView.setImageViewResource (R.id.gps_indicator_view, R.drawable.gps_status_indicator_yellow );
					 break;
				 }
				 
				 case Locked:
				 {
					 gpsString = "Locked";
					 remoteView.setImageViewResource (R.id.gps_indicator_view, R.drawable.gps_status_indicator_green );
					 break;
				 }
			 }
		 }
		 
		 // Set the text fields on the remote views
		 remoteView.setTextViewText( R.id.controlButton, String.format( "%s%s%s", locationString, statusString, utmString ) );
		 remoteView.setTextViewText( R.id.osRefView, refString );
		 remoteView.setTextViewText( R.id.gpsStatusView, gpsString );
		 remoteView.setTextViewText( R.id.accuracyView, accuracyString );
		 
		 // If logging is not running then display everything in grey
		 int displayColor = Color.WHITE;
		 if ( ( receivedStatus == null ) || ( receivedStatus.state != TrackingState.Logging ) )
		 {
			 displayColor = Color.DKGRAY;
			 remoteView.setImageViewResource (R.id.gps_indicator_view, R.drawable.gps_status_indicator_grey );
		 }
		 
		 remoteView.setTextColor( R.id.controlButton, displayColor );
		 remoteView.setTextColor( R.id.osRefView, displayColor );
		 remoteView.setTextColor( R.id.gpsStatusView, displayColor );
		 remoteView.setTextColor( R.id.accuracyView, displayColor );
		 
		 log.info( "buildLayout() OS {} GPS {}",refString, gpsString );
		 
		 return remoteView;
	 }

	  /**
	   * Return a String representation of this OSGB grid reference using the
	   * eight-figure notation in the form XY 1234 5678
	   * 
	   * @return a String representing this OSGB grid reference in eight-figure notation
	   * @since 1.0
	   */
	  public String toEightFigureString( OSRef gbRef ) 
	  {
		  int hundredkmE = ( int )Math.floor( gbRef.getEasting() / 100000 );
		  int hundredkmN = ( int )Math.floor( gbRef.getNorthing() / 100000 );
		  
		  char firstLetter;
		  if ( hundredkmN < 5 ) 
		  {
			  if ( hundredkmE < 5 ) 
			  {
				  firstLetter = 'S';
			  } 
			  else 
			  {
				  firstLetter = 'T';
			  }
		  } 
		  else if ( hundredkmN < 10 ) 
		  {
			  if ( hundredkmE < 5 ) 
			  {
				  firstLetter = 'N';
			  } 
			  else 
			  {
				  firstLetter = 'O';
			  }
		  } 
		  else 
		  {
			  firstLetter = 'H';
		  }

		  int index = 65 + ( ( 4 - ( hundredkmN % 5 ) ) * 5 ) + ( hundredkmE % 5 );
		  if ( index >= 73 )
		  {
			  index++;
		  }
		  
		  char secondLetter = ( char ) index;

		  int e = ( int )Math.round( ( gbRef.getEasting() - ( 100000 * hundredkmE ) ) / 10 );
		  int n =  (int )Math.round( ( gbRef.getNorthing() - ( 100000 * hundredkmN ) ) / 10 );

		  return String.format( "%C%C %04d %04d", firstLetter, secondLetter, e, n );
	  }

	 /** Logger for this class */
	 private static final Logger log = LoggerFactory.getLogger( TrackerControlWidgetProvider.class );
	
	 private static final String CONTROL_BUTTON_CLICKED = "tvs.example.serviceprototype.CONTROL_BUTTON_CLICKED";
	 
	 private static Location receivedLocation = null;
	 
	 private static TrackingStatus receivedStatus = null;
	 
	 private final TrackerServiceManager.UpdateReceiver serviceReceiver = new TrackerServiceManager.UpdateReceiver();
}

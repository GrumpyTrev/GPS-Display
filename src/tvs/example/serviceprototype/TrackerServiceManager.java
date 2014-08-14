package tvs.example.serviceprototype;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.Location;
import android.os.IBinder;
import android.os.RemoteException;

public class TrackerServiceManager 
{
	// Public broadcast constants
	public final static String BROADCAST_LOCATION = "tvs.example.serviceprototype.BROADCAST_LOCATION";
	public final static String BROADCAST_STATUS = "tvs.example.serviceprototype.BROADCAST_STATUS";
	
	public TrackerServiceManager()
	{
	}
	
	public void startup( Context context, final Runnable onServiceConnected )
	{
	    log.info( "startup" );
	    
		synchronized( threadLock )
		{
			if ( bound == false )
			{
	            postBindProcess = onServiceConnected;

				serviceConnection = new ServiceConnection()
	            {
					@Override
					public void onServiceConnected( ComponentName className, IBinder service )
					{
						synchronized( threadLock )
						{
	                    	log.info(  "onServiceConnected() {}", Thread.currentThread().getId() );
							remoteBinding = ITrackerServiceRemote.Stub.asInterface( service );
							bound = true;
						}
						
		                  if ( postBindProcess != null )
		                  {
		                	  postBindProcess.run();
		                	  postBindProcess = null;
		                  }
					}
					
					@Override
					public void onServiceDisconnected( ComponentName className )
					{
						synchronized ( threadLock )
						{
	                    	log.info(  "onServiceDisconnected() {}", Thread.currentThread().getId() );
							bound = false;
						}
					}
	            };
	            
	            context.bindService( new Intent( context, GPSInterfaceService.class ), serviceConnection, Context.BIND_AUTO_CREATE );
			}
			else
			{
				log.info( "Attempting to connect whilst already connected" );
			}
		}
	}

	/**
	 * Means by which an Activity lifecycle aware object hints about binding and unbinding
	 */
	public void shutdown( Context context )
	{
	    log.info( "shutdown" );

	    synchronized( threadLock )
	    {
	    	try
	    	{
	    		if ( bound == true )
	    		{
                	log.info(  "unbinding {}", serviceConnection );
	              
	    			context.unbindService( this.serviceConnection );

	    			remoteBinding = null;
	    			serviceConnection = null;
	    			bound = false;
	    		}
	    	}
	    	catch (IllegalArgumentException e)
	    	{
	    	    log.info( "shutdown - failed to unbind a service {}", e );
	    	}
	    }
	}
	
	public void startLogging()
	{
	     synchronized( threadLock )
	     {
	    	 if ( bound == true )
	         {
	    		 try
	    		 {
	    			 remoteBinding.startLogging();
	    		 }
	    		 catch ( RemoteException exception )
	    		 {
	    			 log.info( "startLogging() Could not start logger. {}", exception );
	    		 }
	         }
	    	 else
	    	 {
    			 log.info( "startLogging() not bound" );
	    	 }
	     }
	}

	public void stopLogging()
	{
	     synchronized( threadLock )
	     {
	    	 if ( bound == true )
	         {
	    		 try
	    		 {
	    			 remoteBinding.stopLogging();
	    		 }
	    		 catch ( RemoteException exception )
	    		 {
	    			 log.info( "stopLogging() Could not stop logger. {}", exception );
	    		 }
	         }
	    	 else
	    	 {
    			 log.info( "stopLogging() not bound" );
	    	 }
	     }
	}

	public GPSInterfaceService.TrackingState loggingState()
	{
		GPSInterfaceService.TrackingState currentState = GPSInterfaceService.TrackingState.Unknown;
		
	     synchronized( threadLock )
	     {
	    	 if ( bound == true )
	         {
	    		 try
	    		 {
	    			 int state = remoteBinding.loggingState();
	    			 currentState = GPSInterfaceService.TrackingState.values() [ state ];
	    		 }
	    		 catch ( RemoteException exception )
	    		 {
	    			 log.info( "loggingState() Could not get state. {}", exception );
	    		 }
	         }
	    	 else
	    	 {
	    		 log.info( "loggingState() not bound" );
	    	 }
	     }

		return currentState;
	}
	
	public TrackingStatus getTrackingStatus()
	{
		TrackingStatus currentState = null;
		
	     synchronized( threadLock )
	     {
	    	 if ( bound == true )
	         {
	    		 try
	    		 {
	    			 currentState = remoteBinding.getTrackingStatus();
	    		 }
	    		 catch ( RemoteException exception )
	    		 {
	    			 log.info( "getTrackingStatus() Could not get status. {}", exception );
	    		 }
	         }
	    	 else
	    	 {
	    		 log.info( "getTrackingStatus() not bound" );
	    	 }
	     }

		return currentState;
	}
	
	public static class TrackingLocation
	{
		public static void LoadIntentWithLocation( Intent intentToLoad, Location locationToLoad )
		{
			intentToLoad.putExtra( EXTRA_LOCATION, locationToLoad );
		}
		
		public static Location LocationFromIntent( Intent intintToUnload )
		{
			return ( Location )intintToUnload.getParcelableExtra( EXTRA_LOCATION );
		}
		
		private final static String EXTRA_LOCATION = "tvs.example.serviceprototype.EXTRA_LOCATION";
	}
		
	/** Logger for this class */
	private static final Logger log = LoggerFactory.getLogger( TrackerServiceManager.class );

	/** Object used for locking */
	private final Object threadLock = new Object();

	private boolean bound = false;

	private ITrackerServiceRemote remoteBinding = null;
	   
	private ServiceConnection serviceConnection = null;

	private Runnable postBindProcess = null;

}

package tvs.example.serviceprototype;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.location.Location;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

/**
 * The TrackerServiceManager is used to provide a client interface to the GPSInterfaceService
 */
public class TrackerServiceManager 
{
	// 
	// Public constants
	//
	
	// Public broadcast constants
	public final static String BROADCAST_LOCATION = "tvs.example.serviceprototype.BROADCAST_LOCATION";
	public final static String BROADCAST_STATUS = "tvs.example.serviceprototype.BROADCAST_STATUS";
	
	// 
	// Public types and interfaces
	//
	
	/**
	 * Interface to allow clients to specify a status callback
	 */
	public interface IStatusReceiver 
	{
		/**
		 * Called when a TrackingStatus has been received from the service
		 * @param receivedStatus
		 */
		public void onStatusReceived( TrackingStatus receivedStatus, Context context );
	}

	/**
	 * Interface to allow clients to specify a location callback
	 */
	public interface ILocationReceiver 
	{
		/**
		 * Called when a Location has been received from the service
		 * @param receivedLocation
		 */
		public void onLocationReceived( Location receivedLocation, Context context );
	}

	//
	// Public methods
	//
	
	/**
	 * Default constructor
	 */
	public TrackerServiceManager()
	{
	}
	
	/**
	 * Constructor supplying status receiver and location receivers
	 * @param statusReceiver - the status receiver (can be null)
	 * @param locationReceiver - the location receiver (can be null)
	 */
	public TrackerServiceManager( IStatusReceiver statusReceiver, ILocationReceiver locationReceiver )
	{
		receiver.SetStatusReceiver( statusReceiver );
		receiver.SetLocationReceiver( locationReceiver );
	}
	
	/**
	 * Called by a client to bind to the GPSInterfaceService
	 * @param context - Context to use for binding
	 * @param onServiceConnected - Command to execute once the service has bound
	 */
	public void bindToService( Context context, final Runnable onServiceConnected )
	{
	    log.info( "bindToService" );
	    
		if ( bound == false )
		{
			// Save the callback for when the service has connected. Must be a class variable because the startup method returns 
			// straight away
            postBindProcess = onServiceConnected;

            // Use the supplied context to set up a BROADCAST_STATUS and BROADCAST_LOCATION receiver.  
            receiver.register( context );
  
            // Define a class to handle the connection/disconnection. Again needs to be a class variable
    		connectionHandler = new ServiceConnection()
            {
    			/**
    			 * Called when the service has bound
    			 */
				@Override
				public void onServiceConnected( ComponentName className, IBinder service )
				{
                	log.info(  "onServiceConnected() {}", Thread.currentThread().getId() );
                	
                	// Create a messenger to send messages to the service
                	serviceMessenger = new Messenger( service );

                	// Initial poll of service status
    				sendMessageToService( GPSInterfaceService.REQUESTSTATUS );

    				bound = true;

					// Call any provided callback
                	if ( postBindProcess != null )
                	{
                		postBindProcess.run();
                		postBindProcess = null;
                	}
				}
				
				/**
				 * Called when the service has unbound
				 */
				@Override
				public void onServiceDisconnected( ComponentName className )
				{
                    	log.info(  "onServiceDisconnected() {}", Thread.currentThread().getId() );
                    	serviceMessenger = null;
						bound = false;
				}
            };
            
            // Perform the connection
            context.bindService( new Intent( context, GPSInterfaceService.class ), connectionHandler, Context.BIND_AUTO_CREATE );
 		}
		else
		{
			log.info( "Attempting to connect whilst already connected" );
		}
	}

	/**
	 * Called by the client to unbind from the GPSInterfaceService
	 * @param context - Context to use for the unbind
	 */
	public void unBindFromService( Context context )
	{
	    log.info( "unBindFromService" );

    	try
    	{
    		if ( bound == true )
    		{
            	log.info(  "unbinding {}", connectionHandler );
              
    			context.unbindService( connectionHandler );
    		    connectionHandler = null;
    		    
    		    receiver.deregister( context );
    		    bound = false;
    		}
    	}
    	catch (IllegalArgumentException e)
    	{
    	    log.info( "unBindFromService - failed to unbind a service {}", e );
    	}
	}
	
	/**
	 * Register a receiver with the context to receiver BROADCAST_STATUS and BROADCAST_LOCATION intents
	 * @param contextForReceivers
	 */
	public void registerReceivers( Context contextForReceivers )
	{
		receiver.register( contextForReceivers );
	}
	
	/**
	 * Unregister the BROADCAST_STATUS and BROADCAST_LOCATION receivers
	 * @param contextForReceivers
	 */
	public void unregisterReceivers( Context contextForReceivers )
	{
		receiver.deregister( contextForReceivers );
	}
	
	/**
	 * Send a STARTLOGGING command to the service
	 */
	public void startLogging()
	{
	   	 if ( bound == true )
	     {
			 sendMessageToService( GPSInterfaceService.STARTLOGGING );
	     }
		 else
		 {
			 log.info( "startLogging() not bound" );
		 }
	}

	/**
	 * Send a STOPLOGGING command to the service
	 */
	public void stopLogging()
	{
	   	 if ( bound == true )
	     {
			 sendMessageToService( GPSInterfaceService.STOPLOGGING );
	     }
		 else
		 {
			 log.info( "stopLogging() not bound" );
		 }
	}

	/**
	 * Return the cached logging state received from the service
	 * @return
	 */
	public GPSInterfaceService.TrackingState loggingState()
	{
		return receiver.loggingState();
	}

	//
	// Private methods and classes
	//

	/**
	 * A BroadcastReceiver for the BROADCAST_STATUS intent
	 */
	public static class UpdateReceiver extends BroadcastReceiver
	{
		/**
		 * Called when a BROADCAST_STATUS has been received
		 * Cache the received value and pass on to any delegates
		 */
		@Override
		public void onReceive( Context context, Intent intent )
		{
			if ( intent.getAction() == BROADCAST_STATUS )
			{
				receivedStatus = TrackingStatus.StatusFromIntent( intent );
				log.info( "onReceive() received BROADCAST_STATUS {}", receivedStatus.state.toString() );
	    		 
				if ( statusReceiver != null )
				{
					statusReceiver.onStatusReceived( receivedStatus, context );
				}
			}
			else if ( intent.getAction() == BROADCAST_LOCATION )
			{
				Location receivedLocation = TrackingLocation.LocationFromIntent( intent );
				log.info( "onReceive() received BROADCAST_LOCATION {}", receivedLocation.toString() );
	    		 
	    		 if ( locationReceiver != null )
	    		 {
	    			 locationReceiver.onLocationReceived( receivedLocation, context );
	    		 }
			}
		}
		
		public void register( Context registrationContext )
		{
			// Only register if not already registered
			if ( registered == false )
			{
		   	    IntentFilter filter = new IntentFilter();
		  	    filter.addAction( BROADCAST_STATUS );
		  	    filter.addAction( BROADCAST_LOCATION );
		   	    
		  	    registrationContext.registerReceiver( this, filter );
		  	    
		  	    registered = true;
			}
		}
		
		public void deregister(  Context registrationContext )
		{
			// Only deregister is registered
			if ( registered == true )
			{
				registrationContext.unregisterReceiver( this );
			}
		}
		
		public void SetStatusReceiver( IStatusReceiver aStatusReceiver )
		{
			statusReceiver = aStatusReceiver;
		}
		
		public void SetLocationReceiver( ILocationReceiver aLocationReceiver )
		{
			locationReceiver = aLocationReceiver;
		}
		
		/**
		 * Return the cached logging state received from the service
		 * @return
		 */
		public GPSInterfaceService.TrackingState loggingState()
		{
			GPSInterfaceService.TrackingState currentState = GPSInterfaceService.TrackingState.Unknown;
			
			if ( receivedStatus != null )
			{
				currentState = receivedStatus.state;
			}

			return currentState;
		}

		private boolean registered = false;
		
		private IStatusReceiver statusReceiver = null;

		private ILocationReceiver locationReceiver = null;
		
		private TrackingStatus receivedStatus = null;

		/** Logger for this class */
		private static final Logger log = LoggerFactory.getLogger( UpdateReceiver.class );
	}
	
	/**
	 * Send a simple command message to the service
	 * @param messageToSend
	 */
	private void sendMessageToService( int messageToSend )
	{
		if ( serviceMessenger != null )
		{
          	try
        	{
				serviceMessenger.send( Message.obtain( null, messageToSend, 0, 0 ) );
			} 
        	catch ( RemoteException e ) 
        	{
				e.printStackTrace();
			}
		}
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

	private boolean bound = false;

	private Runnable postBindProcess = null;

	private ServiceConnection connectionHandler = null;
	
	/** Messenger used to send messages to the service  */
	private Messenger serviceMessenger = null;
	
	private UpdateReceiver receiver = new UpdateReceiver();
	
	private TrackingStatus receivedStatus = null;
}

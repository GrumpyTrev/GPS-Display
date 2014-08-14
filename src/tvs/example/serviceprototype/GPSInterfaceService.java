package tvs.example.serviceprototype;

import java.lang.ref.WeakReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tvs.example.serviceprototype.TrackerServiceManager.TrackingLocation;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.GpsStatus.NmeaListener;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.GpsStatus.Listener;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;

/**
 * Service class used to interface to the GPS via the LocationListener
*/
public class GPSInterfaceService extends Service implements LocationListener
{
	//
	// Public types & enums
	//
	
	/**
	 * The state of the service
	 */
	public enum TrackingState
	{
		Unknown,
		Logging,
		Stopped
	};

	public enum ProviderState
	{
		Disabled,
		NoLock,
		Locked
	};

	//
	// Public methods
	//
	
	/**
	 * Called when the service is first created.
	 * 
	 */
	@Override 
	public void onCreate() 
	{    
		log.info( "onCreate" );

		// Start up the thread running the service.     
		HandlerThread thread = new HandlerThread( ServiceThreadName, Process.THREAD_PRIORITY_BACKGROUND );
		thread.start();        
		
		// Get the HandlerThread's Looper and use it for the thread's Handler     
		serviceLooper = thread.getLooper();    
		serviceHandler = new ServiceHandler( serviceLooper, this );
		
		// Cache the LocationManager
		gpsLocationManager = ( LocationManager )getSystemService( Context.LOCATION_SERVICE );
		
		// Initialise the timers with their delegates
		noLockTimerInstance.registerDelegate( 
				new ITimerExpiry() 
				{
					@Override
					public void OnTimerExpired() 
					{
						log.info( "noLockTimerInstance OnTimerExpired : No lock acquired ");
					}
				}
		);

		lockAquiredTimerInstance.registerDelegate( 
				new ITimerExpiry() 
				{
					@Override
					public void OnTimerExpired() 
					{
						log.info( "lockAquiredTimerInstance OnTimerExpired : Starting polling ");
						Message.obtain( serviceHandler, TURNONGPS ).sendToTarget();
					}
				}
		);
		
		lockLostTimerInstance.registerDelegate( 
				new ITimerExpiry() 
				{
					@Override
					public void OnTimerExpired() 
					{
						log.info( "lockLostTimerInstance OnTimerExpired : Updating status ");
						
						if ( gpsStatus.providerStatus == ProviderState.Locked )
						{
							gpsStatus.providerStatus = ProviderState.NoLock;
							
							BroadcastStatusChange();
						}
					}
				}
		);
		
		improveAccuracyTimer.registerDelegate(	
				new ITimerExpiry() 
				{
					@Override
					public void OnTimerExpired() 
					{
						log.info( "improveAccuracyTimer OnTimerExpired : Stopping GPS ");

						StopStatusAndLocationUpdates();
						lockAquiredTimerInstance.startTimer( LOCKED_POLL_TIME );
					}
				}
		);
	}
	
	/**
	 * Provide a binder for this service
	 * @param bindIntent The Intent that was used to bind to this service
	 * @return The IBinder instance for this service
	 */
	public IBinder onBind( Intent bindIntent ) 
	{
		return binder;
	}

	/**
	 * Called by the system every time a client explicitly starts the service by calling startService
	 * Not processed
	 * @param startIntent The intent that was passed to startService
	 * @param flags Additional data about this start request. Currently either 0, START_FLAG_REDELIVERY, or START_FLAG_RETRY. 
	 * @param startId A unique integer representing this specific request to start. Use with stopSelfResult(int)
	 * @return The IBinder instance for this service
	 */
	@Override
	public int onStartCommand( Intent startIntent, int flags, int startId )
	{
		log.info( "onStartCommand" );

		// Return START_STICKY for compatibility
		return START_STICKY;
	}

	/**
	 * Called by the system to notify a Service that it is no longer used and is being removed.
	 * Send a STOPSERVICE message to the thread
	 */
	@Override  
	public void onDestroy() 
	{  
		log.info( "onDestroy" );
		Message.obtain( serviceHandler, STOPSERVICE ).sendToTarget();
		
		GenericTimer.stopAll();
	}

	/**
	 * Called when the location has changed. 
	 * Load the details into an Intent and broadcast it
	 */
	@Override
	public void onLocationChanged( Location location ) 
	{
		log.info( "onLocationChanged : Lat {}, Lon {}, Accuracy {}", new Object[]{ location.getLatitude(), location.getLongitude(), 
				location.getAccuracy() } );
		
		// If the GPS is locked on and the accuracy is OK then turn off the GPS and start the GPS poll timer
		if ( gpsStatus.providerStatus == ProviderState.Locked )
		{
			if ( lockLostTimerInstance.isTimerRunning() == false )
			{
				if ( location.getAccuracy() <= REQUIRED_ACCURACY )
				{
					// If this is the first time this accuracy has been achieved then start the improveAccuracyTimer timer
					if ( improveAccuracyTimer.isTimerRunning() == false )
					{
						improveAccuracyTimer.startTimer( EXTRA_TIME_AFTER_LOCK );
					}
				}
			}
		}
		
		Intent intent = new Intent( TrackerServiceManager.BROADCAST_LOCATION );
		
		TrackingLocation.LoadIntentWithLocation( intent, location );
		
	    sendBroadcast( intent );
	}

	/**
	 * Called when the provider is disabled by the user
	 * Update the provider status and broadcast it
	 * @param provider - name of the provider that has been disabled
	 */
	@Override
	public void onProviderDisabled( String provider ) 
	{
		log.info( "onProviderDisabled : Provider {}", provider );
		
		// Only report this if the provider is enabled - or if this is the first time
		if ( ( gpsStatus.providerStatus != ProviderState.Disabled ) || ( firstReport == true ) )
		{
			gpsStatus.providerStatus = ProviderState.Disabled;
			firstReport = false;
			
			BroadcastStatusChange();
		}
	}

	/**
	 * Called when the provider is enabled by the user
	 * Update the provider status and broadcast it
	 * @param provider - name of the provider that has been disabled
	 */
	@Override
	public void onProviderEnabled( String provider ) 
	{
		log.info( "onProviderEnabled : Provider {}", provider );
		
		if ( gpsStatus.providerStatus == ProviderState.Disabled )
		{
			gpsStatus.providerStatus = ProviderState.NoLock;
			firstReport = false;
			
			BroadcastStatusChange();
		}
	}

	@Override
	public void onStatusChanged( String provide, int status, Bundle extras ) 
	{
	}

	//
	// Private methods
	// 
	
	/**
	 * Handler that receives messages from the thread
	 * Provided so that messages can be handled by the serviceHandleMessage method
	 */
	private static class ServiceHandler extends Handler 
	{      
		/**
		 * Constructor specifying Looper instance
		 * @param messageLoop Class used to run a message loop for a thread
		 */
		public ServiceHandler( Looper messageLoop, GPSInterfaceService serviceInstance ) 
		{          
			super( messageLoop );      
			referenceToService = new WeakReference<GPSInterfaceService>( serviceInstance );
		}      
		 
		/**
		 * Called when a message has been received 
		 * Pass it back to the service
		 * @param msg - received message
		 */
		@Override 
		public void handleMessage( Message msg ) 
		{        
			GPSInterfaceService service = referenceToService.get();
			if ( service != null )
			{
				service.serviceHandleMessage( msg );
			}	
		}  
		
		/** A reference to the GPSInterfaceService so that serviceHandleMessage can be called */
		private WeakReference<GPSInterfaceService> referenceToService = null;
	}
	  
	/**
	 * Message handler method to do the work off-loaded by mHandler to
	 * GPSLoggerServiceThread
	 * 
	 * @param msg - received message
	 */
	private void serviceHandleMessage( Message msg )
	{
		log.info( "handleMessage {}", msg.what );
		   
		switch ( msg.what )
		{
			case STOPSERVICE:
			{
				// Perform the same operations as STOPLOGGING and then quit the looper
				
				// Stop asking for status and location updates
				StopStatusAndLocationUpdates();
				
				// Record new state
				gpsStatus.state = TrackingState.Stopped;

				// Broadcast state
				BroadcastStatusChange();
	
				// Quit the thread
			    serviceLooper.quit();
				break;
			}
			
			case STARTLOGGING:
			{
				if ( gpsStatus.state == TrackingState.Stopped )
				{
					// Stop and start updates
					StopStatusAndLocationUpdates();
					StartStatusAndLocationUpdates();
					
					// Record new state
					gpsStatus.state = TrackingState.Logging;

					// Start the noLockTimerInstance
					noLockTimerInstance.startTimer( REASONABLE_FIRST_LOCK_TIME );
					
					// Broadcast state
					BroadcastStatusChange();
				}
				
				break;
			}
			
			case STOPLOGGING:
			{
				gpsStatus.state = TrackingState.Stopped;
				
				// Stop asking for status and location updates
				StopStatusAndLocationUpdates();

				noLockTimerInstance.stopTimer();
				lockAquiredTimerInstance.stopTimer();
				lockLostTimerInstance.stopTimer();
				improveAccuracyTimer.stopTimer();
				
				// Broadcast state
				BroadcastStatusChange();
				
			    break;
			}
		
			case TURNONGPS:
			{
				StartStatusAndLocationUpdates();

				break;
			}
		}
	}

	private void StopStatusAndLocationUpdates()
	{
		gpsLocationManager.removeUpdates( this );
		gpsLocationManager.removeGpsStatusListener( statusListener );
		gpsLocationManager.removeNmeaListener( nmeaListener );
	}
	
	private void StartStatusAndLocationUpdates()
	{
		// Ask for status and location updates
		gpsLocationManager.requestLocationUpdates( LocationManager.GPS_PROVIDER, GPS_INTERVAL, GPS_DISTANCE, this );
		gpsLocationManager.addGpsStatusListener( statusListener );
		gpsLocationManager.addNmeaListener( nmeaListener );
		
		// Get the current status from the statusListener in case it has changed whilst we weren't listening
		if ( gpsLocationManager.isProviderEnabled( LocationManager.GPS_PROVIDER ) == true )
		{
			if ( gpsStatus.providerStatus ==  ProviderState.Locked )
			{
				if ( lockLostTimerInstance.isTimerRunning() == false )
				{
					lockLostTimerInstance.startTimer( LOCK_LOST_FILTER_TIME);
				}
			}
			else
			{
				gpsStatus.providerStatus = ProviderState.NoLock;
			}
		}
		else
		{
			gpsStatus.providerStatus = ProviderState.Disabled;
		}
	}
	
	/**
	 * Listener instance used for receiving notifications when GPS status has changed
	 */
	private Listener statusListener = new GpsStatus.Listener()
	{
		/**
		 * Called to report changes in the GPS status
		 * @param event - one of 
		 * GPS_EVENT_STARTED 
		 * GPS_EVENT_STOPPED 
		 * GPS_EVENT_FIRST_FIX 
		 * GPS_EVENT_SATELLITE_STATUS 
		 */
		@Override
		public synchronized void onGpsStatusChanged( int event )
		{
			GpsStatus status = gpsLocationManager.getGpsStatus( null );

			switch ( event )
			{
				case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
				case GpsStatus.GPS_EVENT_FIRST_FIX:
				{
					// Count number of satellites in the fix
					int newSatellitesInFix = 0;
					int newNumberOfSatellites = 0;

					Iterable<GpsSatellite> satellites = status.getSatellites();
					 
					for ( GpsSatellite satellite : satellites )
					{
						log.info( "Sat {} Id {} Noise {} Fix {}", new Object[]  { newNumberOfSatellites, satellite.getPrn(), satellite.getSnr(),
								satellite.usedInFix() } );
						newNumberOfSatellites++;
						if ( satellite.usedInFix() == true )
						{
							newSatellitesInFix++;
						}
					}
					
					if ( ( newNumberOfSatellites != gpsStatus.satellitesInView ) || ( newSatellitesInFix != gpsStatus.satellitesInFix ) )
					{
						gpsStatus.satellitesInView = newNumberOfSatellites;
						gpsStatus.satellitesInFix = newSatellitesInFix;
						
						if ( newSatellitesInFix > 0 )
						{
							lockLostTimerInstance.stopTimer();
							gpsStatus.providerStatus = ProviderState.Locked;
						}
						else
						{
							improveAccuracyTimer.stopTimer();
							
							if ( gpsStatus.providerStatus ==  ProviderState.Locked )
							{
								if ( lockLostTimerInstance.isTimerRunning() == false )
								{
									lockLostTimerInstance.startTimer( LOCK_LOST_FILTER_TIME);
								}
							}
							else
							{
								gpsStatus.providerStatus = ProviderState.NoLock;
							}
						}
						
						BroadcastStatusChange();
					}
					
					break;
				}
				
				case GpsStatus.GPS_EVENT_STOPPED:
	            case GpsStatus.GPS_EVENT_STARTED:
	            {
	            	break;
	            }
	            default:
	            {
	               break;
	            }
			}
		}
	};

	private NmeaListener nmeaListener = new GpsStatus.NmeaListener() 
	{
		@Override
		public void onNmeaReceived( long timeStamp, String nmeaBuffer ) 
		{
			String removeLinefeed = nmeaBuffer.replace( '\n', ' ' );
			log.info( String .format( "Time %d : [%s]", timeStamp, removeLinefeed ) );
		}
	};
	
	/**
	 * Load an Intent with the current status and broadcast it
	 */
	private void BroadcastStatusChange()
	{
		Intent intent = new Intent( TrackerServiceManager.BROADCAST_STATUS );
		gpsStatus.LoadIntentWithStatus( intent );
	    sendBroadcast( intent );
	}
	
	private IBinder binder = new ITrackerServiceRemote.Stub()
	{
		@Override
		public int loggingState() throws RemoteException
		{
			return gpsStatus.state.ordinal();
		}

		@Override
		public TrackingStatus getTrackingStatus() throws RemoteException
		{
			return gpsStatus;
		}

		@Override
		public void startLogging() throws RemoteException
		{
			Message.obtain( serviceHandler, STARTLOGGING ).sendToTarget();
		}

		@Override
		public void stopLogging() throws RemoteException
		{
			Message.obtain( serviceHandler, STOPLOGGING ).sendToTarget();
		}
	};

	//
	// Private data
	//
	
	/** Use a TrackingStatus instance to hold all the status data */
	private TrackingStatus gpsStatus = new TrackingStatus( TrackingState.Stopped, ProviderState.Disabled, 0, 0 );
	
	/** Logger for this class */
	private static final Logger log = LoggerFactory.getLogger( GPSInterfaceService.class );
	  
	/** Looper used by the service thread */
	private Looper serviceLooper = null;
	  
	/** The thread's message handler */
	private ServiceHandler serviceHandler = null;
	  
	/** Cached LocationManager */
	private LocationManager gpsLocationManager = null;
	
	private boolean firstReport = true;

	private GenericTimer lockAquiredTimerInstance = new GenericTimer();
	
	private GenericTimer noLockTimerInstance = new GenericTimer();
	
	private GenericTimer lockLostTimerInstance = new GenericTimer();
	
	private GenericTimer improveAccuracyTimer = new GenericTimer();
	
	/** Name given to the thread that actually interfaces to the GPS */
	private static final String ServiceThreadName = "GPSInterfaceThread";
	
	/** Thread message codes */
	private static final int STOPSERVICE = 1;
	private static final int STARTLOGGING = 2;
	private static final int STOPLOGGING = 3;
	private static final int TURNONGPS=4;
	
	private static final float GPS_DISTANCE = 0F;
	private static final long  GPS_INTERVAL = 0l;
	
	private static final float REQUIRED_ACCURACY = 20F;
	
	/*
	 * How often to ask the GPS for a new lock once a lock has been obtained
	 */
	private static final long LOCKED_POLL_TIME = 15000;
	
	/*
	 * How long to wait for the first lock
	 */
	private static final long REASONABLE_FIRST_LOCK_TIME = 30000;
	
	/*
	 * How long to wait for a subsequent lock after a lock has been acquired
	 */
	private static final long LOCK_LOST_FILTER_TIME = 10000;
	
	/*
	 * How long to extend the time the GPS is turned on in hope of getting a more accurate fix
	 */
	private static final long EXTRA_TIME_AFTER_LOCK = 2000;
}


package tvs.example.serviceprototype;

import tvs.example.serviceprototype.GPSInterfaceService.ProviderState;
import tvs.example.serviceprototype.GPSInterfaceService.TrackingState;
import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;

public final class TrackingStatus implements Parcelable
{
	public TrackingState state;
	public ProviderState providerStatus;
	public int satellitesInView;
	public int satellitesInFix;

	public TrackingStatus( TrackingState state, ProviderState providerStatus, int satellitesInView, int satellitesInFix )
	{
		this.state = state;
		this.providerStatus = providerStatus;
		this.satellitesInView = satellitesInView;
		this.satellitesInFix = satellitesInFix;
	}
	
	@Override
	/**
	 * Describe the kinds of special objects contained in this Parcelable's marshaled representation
	 * @return a bitmask indicating the set of special object types marshalled by the Parcelable.
	*/
	public int describeContents() 
	{
		// Nothing special
		return 0;
	}

	@Override
	/**
	 * Flatten this object in to a Parcel
	 * @param dest - The Parcel in which the object should be written. 
	 * @param flags - Additional flags about how the object should be written. May be 0 or PARCELABLE_WRITE_RETURN_VALUE.
	 */
	public void writeToParcel( Parcel dest, int flags ) 
	{
		dest.writeInt( state.ordinal() );
		dest.writeInt( providerStatus.ordinal() );
		dest.writeInt( satellitesInView );
		dest.writeInt( satellitesInFix );
	}
	
    public void readFromParcel( Parcel in ) 
    {       
    	state = TrackingState.values()[ in.readInt() ];        
    	providerStatus = ProviderState.values()[ in.readInt() ];        
       	satellitesInView = in.readInt();        
       	satellitesInFix = in.readInt();        
    }
    
    public static final Parcelable.Creator<TrackingStatus> CREATOR = new Parcelable.Creator<TrackingStatus>() 
    {        
    	public TrackingStatus createFromParcel (Parcel in ) 
    	{            
    		return new TrackingStatus( in );
    	}        
    	
    	public TrackingStatus[] newArray (int size ) 
    	{            
    		return new TrackingStatus[size];
    	}    
    };
    
	public void LoadIntentWithStatus( Intent intentToLoad )
	{
		intentToLoad.putExtra( EXTRA_STATE, state.ordinal() );
		intentToLoad.putExtra( EXTRA_PROVIDER_STATUS, providerStatus.ordinal() );
		intentToLoad.putExtra( EXTRA_SATELLITES, satellitesInView );
		intentToLoad.putExtra( EXTRA_SATELLITES_IN_FIX, satellitesInFix );
	}
	
	public static TrackingStatus StatusFromIntent( Intent intentToUnload )
	{
		TrackingStatus status = new TrackingStatus( 
				TrackingState.values() [ intentToUnload.getIntExtra(EXTRA_STATE, 0) ],
				ProviderState.values() [ intentToUnload.getIntExtra(EXTRA_PROVIDER_STATUS, 0) ],
				intentToUnload.getIntExtra( EXTRA_SATELLITES, 0 ),
				intentToUnload.getIntExtra( EXTRA_SATELLITES_IN_FIX, 0 ) );

		return status;
	}
	
    private TrackingStatus( Parcel in ) 
    {        
    	readFromParcel( in );    
    }
    
	private final static String EXTRA_STATE = "tvs.example.serviceprototype.EXTRA_STATE";
	private final static String EXTRA_PROVIDER_STATUS = "tvs.example.serviceprototype.EXTRA_PROVIDER_STATUS";
	private final static String EXTRA_SATELLITES = "tvs.example.serviceprototype.EXTRA_SATELLITES";
	private final static String EXTRA_SATELLITES_IN_FIX = "tvs.example.serviceprototype.EXTRA_SATELLITES_IN_FIX";
}

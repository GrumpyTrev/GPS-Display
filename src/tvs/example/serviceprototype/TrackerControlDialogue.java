package tvs.example.serviceprototype;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;

public class TrackerControlDialogue extends Activity
{
	Context contextForDialogue = null;
	
	public TrackerControlDialogue()
	{
	}

	@Override
	protected void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );
		
		contextForDialogue = this;
		serviceManager = new TrackerServiceManager();
		
	    setVisible( false );
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		
		serviceManager.startup( this, new Runnable()
		{
            @Override
            public void run()
            {
               showDialog( TRACKER_CONTROL_DIALOGUE );
            }
		} );
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		
		serviceManager.shutdown( this );
	}

	@Override
	protected Dialog onCreateDialog( int id )
	{
		Dialog createdDialog = null;

		switch( id )
		{
			case TRACKER_CONTROL_DIALOGUE:
			{
				log.info( "onCreateDialog - TRACKER_CONTROL_DIALOGUE" );

				AlertDialog.Builder builder = new AlertDialog.Builder( this );    
				 
				// Get the layout inflater    
				LayoutInflater inflater = LayoutInflater.from( this );    
				 
				// Inflate and set the layout for the dialog    
				// Pass null as the parent view because its going in the dialog layout   
				View dialogueView = inflater.inflate( R.layout.tracker_control_dialogue, null );
				
				builder.setView( dialogueView )    
					// Add cancel action button
					.setNegativeButton( "Cancel", new OnClickListener() 
					{               
						public void onClick( DialogInterface dialog, int id ) 
						{                   
							log.info( "Cancel - OnClick" );

							setResult( RESULT_CANCELED,  new Intent() );
							finish();
						 }
					 } )
					.setCancelable( true )
					.setOnCancelListener( new DialogInterface.OnCancelListener() 
					{
						public void onCancel( DialogInterface dialog ) 
						{
							log.info( "Cancel - OnCancel" );

							setResult( RESULT_CANCELED,  new Intent() );
							finish();
						}
					} );     
				
				// Create the dialogue
				createdDialog = builder.create();

				// Cache the button controls
	            startButton = ( Button ) dialogueView.findViewById( R.id.tracker_control_start );
	            stopButton = ( Button ) dialogueView.findViewById( R.id.tracker_control_stop );
	            aboutButton = ( Button ) dialogueView.findViewById( R.id.about );
	 
	            startButton.setOnClickListener( new View.OnClickListener()
		            {
		            	public void onClick( View senderView )
		                {
		            		serviceManager.startLogging();
		                    setResult( RESULT_OK, new Intent() );
		                    finish();
		                }	
		            }
	            );

	            stopButton.setOnClickListener( new View.OnClickListener()
		            {
		            	public void onClick( View senderView )
		                {
		            		serviceManager.stopLogging();
		                    setResult( RESULT_OK, new Intent() );
		                    finish();
		                }	
		            }
	            );

	            aboutButton.setOnClickListener( new View.OnClickListener()
		            {
		            	public void onClick( View senderView )
		                {
		            		AboutDialogue about = new AboutDialogue( contextForDialogue );
		            		about.setTitle( "About this app" );
		            		about.show();
		                }	
		            }
	            );

				break;
			}
			
			default:
			{
				break;
			}
		}
		
		return createdDialog;
	}
	 
	@Override
	protected void onPrepareDialog( int id, Dialog dialog )
	{
		switch( id )
		{
			case TRACKER_CONTROL_DIALOGUE:
			{
				updateDialogueState( serviceManager.loggingState() );
				break;
			}
			
			default:
			{
				break;
	      	}
		}
		
		super.onPrepareDialog( id, dialog );
	}
	   
	private void updateDialogueState( GPSInterfaceService.TrackingState state )
	{
		startButton.setEnabled( false );
		stopButton.setEnabled( false );

		switch( state )
		{
	     	case Stopped:
	     	{
	     		startButton.setEnabled( true );
	            break;
	     	}
	     	
	     	case Logging:
	     	{
	     		stopButton.setEnabled( true );
	            break;
	     	}
	     	
	        case Unknown:
	        {
	        	break;
	        }
		}
	}

	/** Logger for this class */
	private static final Logger log = LoggerFactory.getLogger( TrackerControlDialogue.class );
	
	private Button startButton = null;
	private Button stopButton = null;
	private Button aboutButton = null;
	
	private TrackerServiceManager serviceManager = null;
	
	private static final int TRACKER_CONTROL_DIALOGUE = 1;
}

package tvs.example.serviceprototype;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Generic TimerTask used to notify client that lock has not been acquired within a reasonable period
 */
public class GenericTimer
{
	public GenericTimer()
	{
	}

	public void registerDelegate(  ITimerExpiry expiryDelegate )
	{
		delegate = expiryDelegate;		
	}
	
	public void startTimer( long timePeriod )
	{
		if ( theTimerTask != null )
		{
			theTimerTask.cancel();
		}
		
		theTimerTask = new InnerTimer();
		theTimerThread.schedule( theTimerTask, timePeriod );
		timerRunning = true;
	}
	
	public void startTimer( long timeDelay,  long timePeriod )
	{
		if ( theTimerTask != null )
		{
			theTimerTask.cancel();
		}
		
		theTimerTask = new InnerTimer();
		theTimerThread.schedule( theTimerTask, timeDelay, timePeriod );
		timerRunning = true;
	}
	
	public void stopTimer()
	{
		if ( theTimerTask != null )
		{
			theTimerTask.cancel();
			theTimerTask = null;
		}
		
		timerRunning = false;
	}
	
	public Boolean isTimerRunning()
	{
			return timerRunning;
	}
	
	public static void stopAll()
	{
		theTimerThread.cancel();
		theTimerThread.purge();
	}
	
	private void innerTimerExpired()
	{
		timerRunning = false;

		delegate.OnTimerExpired();
	}
	
	private class InnerTimer extends TimerTask
	{
		@Override
		public void run()
		{
			innerTimerExpired();
		}
	}
	
	private InnerTimer theTimerTask = null;
	private Boolean timerRunning = false;
	private ITimerExpiry delegate = null;
	
	private static Timer theTimerThread = new Timer();
};



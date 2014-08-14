package tvs.example.serviceprototype;

import tvs.example.serviceprototype.TrackingStatus;

interface ITrackerServiceRemote {

	int loggingState();
	TrackingStatus getTrackingStatus();
    void startLogging();
	void stopLogging();
}
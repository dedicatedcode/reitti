class TripDataProvider {
    constructor(dataUrl) {
        this.dataUrl = dataUrl;
        this.data = [];
        this.markers = [];
    }

    async load(signal) {
        return fetch(window.contextPath + this.dataUrl, signal)
            .then(response => response.json())
            .then(data => this.data = data);
    }

    destroy() {
        this.markers.forEach(marker => marker.remove());
    }

    render(map) {
        if (!this.data || this.data.length === 0) return;

        // 1. Sort by UTC Start Time
        const sortedTrips = [...this.data].sort((a, b) =>
            new Date(a.startTime).getTime() - new Date(b.startTime).getTime()
        );

        const journeyStartMs = new Date(sortedTrips[0].startVisit.endTime).getTime();

        sortedTrips.forEach((trip, index) => {
            const isFirstVisitOfJourney = index === 0;
            const isLastTrip = index === sortedTrips.length - 1;
            this.createVisitMarker(map, trip.startVisit, isFirstVisitOfJourney, false, journeyStartMs);
            if (isLastTrip) {
                this.createVisitMarker(map, trip.endVisit, false, true, journeyStartMs);

            }
        });
    }

    createVisitMarker(map, visit, isFirst, isLast, journeyStartMs) {
            const el = document.createElement('div');
            el.className = isFirst ? 'marker-start' : (isLast ? 'marker-end' : 'marker-waypoint');

            const arrivalDate = new Date(visit.startTime);
            const departureDate = new Date(visit.endTime);

            // Time format config
            const timeOptions = { hour: '2-digit', minute: '2-digit' };

            // Journey & Duration Math
            const elapsedMs = arrivalDate.getTime() - journeyStartMs;
            const elapsedHrs = Math.floor(elapsedMs / 3600000);
            const elapsedMins = Math.floor((elapsedMs % 3600000) / 60000);

            const stayMins = Math.floor(visit.durationSeconds / 60);
            const displayStay = stayMins >= 60
                ? `${Math.floor(stayMins / 60)}h ${stayMins % 60}m`
                : `${stayMins}m`;

            let bodyContent = '';

            if (isFirst) {
                // FIRST MARKER: Focus on the start of the journey
                bodyContent = `
            <p><span>Started at:</span> ${departureDate.toLocaleTimeString([], timeOptions)}</p>
            <p style="font-size: 11px; color: #666;">Origin Point</p>
        `;
            } else if (isLast) {
                // LAST MARKER: Focus on the arrival and total journey time
                bodyContent = `
            <p><span>Arrived:</span> ${arrivalDate.toLocaleTimeString([], timeOptions)}</p>
            <div class="journey-footer">⏱ <strong>+${elapsedHrs}h ${elapsedMins}m</strong> from origin</div>
        `;
            } else {
                // INTERMEDIATE: Full context
                bodyContent = `
            <p><span>Arrived:</span> ${arrivalDate.toLocaleTimeString([], timeOptions)}</p>
            <p><span>Left:</span> ${departureDate.toLocaleTimeString([], timeOptions)}</p>
            <p><span>Stay:</span> ${displayStay}</p>
            <div class="journey-footer">⏱ <strong>+${elapsedHrs}h ${elapsedMins}m</strong> from origin</div>
        `;
            }

            const popupHTML = `
        <div class="map-popup-card">
            <div class="map-popup-card-header">
                <strong>${visit.name.split(',')[0]}</strong>
            </div>
            <div class="popup-body">
                ${bodyContent}
            </div>
        </div>
    `;
        new maplibregl.Marker({element: el})
            .setLngLat([visit.longitudeCentroid, visit.latitudeCentroid])
            .setPopup(new maplibregl.Popup({
                offset: 15,
                closeButton: false,
                className: 'custom-map-popup'
            }).setHTML(popupHTML))
            .addTo(map);
    }
}

class VisitDataProvider {
    constructor(dataUrl) {
        this.dataUrl = dataUrl;
        this.data = [];
        this.markers = [];
    }

    async load(signal) {
        return fetch(window.contextPath + this.dataUrl, signal)
            .then(response => response.json())
            .then(data => this.data = data);
    }

    destroy() {
        this.markers.forEach(marker => marker.remove());
    }

    render(map) {
        this.data.forEach(visit => {
            const {longitudeCentroid: lng, latitudeCentroid: lat, name, startTime, endTime, durationSeconds} = visit;
            const container = document.createElement('div');
            container.className = 'map-marker-ring';
            const hours = Math.floor(durationSeconds / 3600);
            const minutes = Math.floor((durationSeconds % 3600) / 60);
            const durationText = hours > 0 ? `${hours}h ${minutes}m` : `${minutes}m ${durationSeconds % 60}s`;

            const marker = new maplibregl.Marker({
                element: container, anchor: 'center'
            }).setLngLat([lng, lat]).addTo(map);

            const popupContent = `
            <div class="map-popup-card">
                <div class="map-popup-card-header">
                    <strong>${name || 'Unknown Visit'}</strong>
                </div>
                <div class="popup-body">
                    <p><span>Time:</span> ${new Date(startTime).toLocaleTimeString([], {
                hour: '2-digit',
                minute: '2-digit'
            })} - ${new Date(endTime).toLocaleTimeString([], {hour: '2-digit', minute: '2-digit'})}</p>
                    <p><span>Duration:</span> ${durationText}</p>
                </div>
            </div>
        `;
            const popup = new maplibregl.Popup({
                offset: 15,
                closeButton: false,
                className: 'custom-map-popup'
            }).setHTML(popupContent);

            marker.setPopup(popup);
            this.markers.push(marker);
        })

    }
}
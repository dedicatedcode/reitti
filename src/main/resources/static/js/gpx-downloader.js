class GpxDownloader {
    constructor() {
        this.isDownloading = false;
    }

    async downloadGpx(startDate, endDate, buttonElement, relevantData = false) {
        if (this.isDownloading) {
            return;
        }

        this.isDownloading = true;
        this.setLoadingState(buttonElement, true);

        try {
            const timezone = getUserTimezone();
            const params = new URLSearchParams({
                startDate: startDate,
                endDate: endDate,
                timezone: timezone,
                relevantData: relevantData ? 'true' : 'false'
            });

            const response = await fetch(`/settings/export-data/gpx?${params}`, {
                method: 'GET',
                headers: {
                    'Accept': 'application/xml'
                }
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            // Get the filename from the Content-Disposition header
            const contentDisposition = response.headers.get('Content-Disposition');
            let filename = 'location_data.gpx';
            if (contentDisposition) {
                const filenameMatch = contentDisposition.match(/filename="(.+)"/);
                if (filenameMatch) {
                    filename = filenameMatch[1];
                }
            }

            // Create blob and download
            const blob = await response.blob();
            this.downloadBlob(blob, filename);

        } catch (error) {
            console.error('Error downloading GPX file:', error);
            this.showError('Fehler beim Herunterladen der GPX-Datei: ' + error.message);
        } finally {
            this.isDownloading = false;
            this.setLoadingState(buttonElement, false);
        }
    }

    downloadBlob(blob, filename) {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.style.display = 'none';
        a.href = url;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        window.URL.revokeObjectURL(url);
        document.body.removeChild(a);
    }

    setLoadingState(buttonElement, isLoading) {
        const loadingIndicator = document.getElementById('gpx-loading');
        
        if (isLoading) {
            buttonElement.disabled = true;
            buttonElement.style.opacity = '0.6';
            if (loadingIndicator) {
                loadingIndicator.style.display = 'inline-block';
            }
        } else {
            buttonElement.disabled = false;
            buttonElement.style.opacity = '1';
            if (loadingIndicator) {
                loadingIndicator.style.display = 'none';
            }
        }
    }

    showError(message) {
        // Create or update error message
        let errorDiv = document.getElementById('gpx-error');
        if (!errorDiv) {
            errorDiv = document.createElement('div');
            errorDiv.id = 'gpx-error';
            errorDiv.className = 'alert alert-danger';
            errorDiv.style.marginTop = '10px';
            
            const gpxButton = document.querySelector('[data-gpx-download]');
            if (gpxButton && gpxButton.parentNode) {
                gpxButton.parentNode.appendChild(errorDiv);
            }
        }
        
        errorDiv.textContent = message;
        errorDiv.style.display = 'block';
        
        // Auto-hide after 5 seconds
        setTimeout(() => {
            if (errorDiv) {
                errorDiv.style.display = 'none';
            }
        }, 5000);
    }
}

// Initialize the downloader
const gpxDownloader = new GpxDownloader();

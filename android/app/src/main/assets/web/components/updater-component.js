/**
 * DailyTimer Updater Component
 * Encapsulated, self-contained custom element for checking, downloading,
 * tracking progress, and launching APK installation.
 */

class DailyTimerUpdater extends HTMLElement {
  constructor() {
    super();
    this.state = {
      status: 'IDLE', // IDLE | CHECKING | UP_TO_DATE | AVAILABLE | DOWNLOADING | READY_TO_INSTALL | ERROR
      appName: 'Daily Timer',
      versionName: '1.0.0',
      versionCode: 1,
      availableVersion: null,
      progress: 0,
      bytesRead: 0,
      totalBytes: 0,
      errorMessage: '',
      endpointUrl: 'https://raw.githubusercontent.com/shah-ashish/daily-Timer/main/version.json'
    };
  }

  connectedCallback() {
    this.initAppInfo();
    this.setupEventListener();
    this.render();
  }

  initAppInfo() {
    if (window.AndroidUpdater && typeof window.AndroidUpdater.getAppInfo === 'function') {
      try {
        const info = JSON.parse(window.AndroidUpdater.getAppInfo());
        this.state.appName = info.appName || this.state.appName;
        this.state.versionName = info.versionName || this.state.versionName;
        this.state.versionCode = info.versionCode || this.state.versionCode;
      } catch (e) {
        console.error('Error fetching app info', e);
      }
    }
  }

  setupEventListener() {
    window.addEventListener('onUpdateStatus', (event) => {
      const detail = event.detail || {};
      const nextStatus = detail.state;

      if (!nextStatus) return;

      if (nextStatus === 'CHECKING') {
        this.state.status = 'CHECKING';
      } else if (nextStatus === 'UP_TO_DATE') {
        this.state.status = 'UP_TO_DATE';
      } else if (nextStatus === 'AVAILABLE') {
        this.state.status = 'AVAILABLE';
        this.state.availableVersion = detail;
      } else if (nextStatus === 'DOWNLOADING') {
        this.state.status = 'DOWNLOADING';
        this.state.progress = detail.progress || 0;
        this.state.bytesRead = detail.bytesRead || 0;
        this.state.totalBytes = detail.totalBytes || 0;
      } else if (nextStatus === 'READY_TO_INSTALL') {
        this.state.status = 'READY_TO_INSTALL';
      } else if (nextStatus === 'ERROR') {
        this.state.status = 'ERROR';
        this.state.errorMessage = detail.message || 'Update failed';
      }

      this.render();
    });
  }

  handleCheckUpdate() {
    this.state.status = 'CHECKING';
    this.render();

    if (window.AndroidUpdater && typeof window.AndroidUpdater.checkUpdate === 'function') {
      window.AndroidUpdater.checkUpdate(this.state.endpointUrl);
    } else {
      // Mock for browser testing
      setTimeout(() => {
        this.state.status = 'AVAILABLE';
        this.state.availableVersion = {
          versionName: '1.0.1',
          downloadUrl: 'https://example.com/update.apk',
          releaseNotes: 'Performance enhancements and bug fixes'
        };
        this.render();
      }, 1000);
    }
  }

  handleDownload() {
    const downloadUrl = this.state.availableVersion?.downloadUrl;
    if (!downloadUrl) return;

    this.state.status = 'DOWNLOADING';
    this.state.progress = 0;
    this.render();

    if (window.AndroidUpdater && typeof window.AndroidUpdater.downloadUpdate === 'function') {
      window.AndroidUpdater.downloadUpdate(downloadUrl);
    } else {
      // Mock progress simulation for browser preview
      let currentProgress = 0;
      const interval = setInterval(() => {
        currentProgress += 10;
        this.state.progress = currentProgress;
        this.state.bytesRead = (currentProgress * 200000);
        this.state.totalBytes = 2000000;
        this.render();
        if (currentProgress >= 100) {
          clearInterval(interval);
          this.state.status = 'READY_TO_INSTALL';
          this.render();
        }
      }, 300);
    }
  }

  handleInstall() {
    if (window.AndroidUpdater && typeof window.AndroidUpdater.installUpdate === 'function') {
      window.AndroidUpdater.installUpdate();
    } else {
      alert('Mock: Triggering APK Installation...');
    }
  }

  render() {
    const { status, appName, versionName, availableVersion, progress, bytesRead, totalBytes, errorMessage } = this.state;
    const mbRead = (bytesRead / (1024 * 1024)).toFixed(1);
    const mbTotal = (totalBytes / (1024 * 1024)).toFixed(1);

    this.innerHTML = `
      <div class="p-4 bg-white dark:bg-[#1C1C1E] rounded-2xl border border-neutral-200/80 dark:border-neutral-800 shadow-sm transition-all duration-200">
        <!-- Top Row: Icon, App Details, Action Button -->
        <div class="flex items-center justify-between gap-3">
          <div class="flex items-center gap-3">
            <img src="logo.png" onerror="this.style.display='none'" class="w-11 h-11 rounded-xl object-contain shadow-sm" alt="App Logo" />
            <div>
              <h3 class="text-[16px] font-semibold text-neutral-900 dark:text-neutral-100 tracking-tight leading-tight">${appName}</h3>
              <p class="text-xs text-neutral-500 dark:text-neutral-400 font-medium">Version ${versionName}</p>
            </div>
          </div>

          <!-- Dynamic Action Button -->
          <div>
            ${status === 'IDLE' || status === 'UP_TO_DATE' ? `
              <button id="btn-check" class="px-3.5 py-1.5 text-xs font-semibold rounded-full bg-neutral-100 dark:bg-neutral-800 text-neutral-800 dark:text-neutral-200 hover:bg-neutral-200 active:scale-95 transition-all">
                ${status === 'UP_TO_DATE' ? 'Up to date' : 'Check for Update'}
              </button>
            ` : ''}

            ${status === 'CHECKING' ? `
              <button disabled class="px-3.5 py-1.5 text-xs font-semibold rounded-full bg-neutral-100 dark:bg-neutral-800 text-neutral-500 flex items-center gap-1.5 cursor-wait">
                <svg class="animate-spin h-3.5 w-3.5 text-[#FF5722]" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                </svg>
                Checking...
              </button>
            ` : ''}

            ${status === 'AVAILABLE' ? `
              <button id="btn-download" class="px-3.5 py-1.5 text-xs font-semibold rounded-full bg-[#FF5722] text-white hover:bg-[#F4511E] active:scale-95 shadow-sm shadow-orange-500/30 transition-all">
                Download v${availableVersion?.versionName || ''}
              </button>
            ` : ''}

            ${status === 'READY_TO_INSTALL' ? `
              <button id="btn-install" class="px-3.5 py-1.5 text-xs font-semibold rounded-full bg-[#34C759] text-white hover:bg-[#2EBD53] active:scale-95 shadow-sm shadow-green-500/30 transition-all">
                Install Now
              </button>
            ` : ''}

            ${status === 'ERROR' ? `
              <button id="btn-retry" class="px-3.5 py-1.5 text-xs font-semibold rounded-full bg-red-100 dark:bg-red-900/30 text-red-600 dark:text-red-400 hover:bg-red-200 active:scale-95 transition-all">
                Retry Check
              </button>
            ` : ''}
          </div>
        </div>

        <!-- Release Notes Section (when update available) -->
        ${status === 'AVAILABLE' && availableVersion?.releaseNotes ? `
          <div class="mt-3 p-2.5 rounded-xl bg-orange-50/70 dark:bg-orange-950/20 border border-orange-200/50 dark:border-orange-800/30 text-xs text-orange-900 dark:text-orange-200">
            <span class="font-semibold">What's New:</span> ${availableVersion.releaseNotes}
          </div>
        ` : ''}

        <!-- Active Progress Bar Section (when downloading) -->
        ${status === 'DOWNLOADING' ? `
          <div class="mt-3.5 space-y-1.5">
            <div class="flex justify-between items-center text-xs text-neutral-600 dark:text-neutral-400 font-medium">
              <span>Downloading update...</span>
              <span class="font-semibold text-neutral-900 dark:text-neutral-100">${progress}%</span>
            </div>
            <div class="w-full h-2 rounded-full bg-neutral-100 dark:bg-neutral-800 overflow-hidden">
              <div class="h-full bg-gradient-to-r from-[#FF5722] to-[#FF8A65] transition-all duration-150 rounded-full" style="width: ${progress}%"></div>
            </div>
            ${totalBytes > 0 ? `
              <div class="text-[11px] text-neutral-400 dark:text-neutral-500 text-right">
                ${mbRead} MB / ${mbTotal} MB
              </div>
            ` : ''}
          </div>
        ` : ''}

        <!-- Error feedback -->
        ${status === 'ERROR' && errorMessage ? `
          <div class="mt-3 text-xs text-red-600 dark:text-red-400 font-medium flex items-center gap-1">
            <svg class="w-4 h-4 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <span>${errorMessage}</span>
          </div>
        ` : ''}
      </div>
    `;

    // Attach button handlers
    const btnCheck = this.querySelector('#btn-check');
    if (btnCheck) btnCheck.addEventListener('click', () => this.handleCheckUpdate());

    const btnDownload = this.querySelector('#btn-download');
    if (btnDownload) btnDownload.addEventListener('click', () => this.handleDownload());

    const btnInstall = this.querySelector('#btn-install');
    if (btnInstall) btnInstall.addEventListener('click', () => this.handleInstall());

    const btnRetry = this.querySelector('#btn-retry');
    if (btnRetry) btnRetry.addEventListener('click', () => this.handleCheckUpdate());
  }
}

customElements.define('daily-timer-updater', DailyTimerUpdater);
export { DailyTimerUpdater };

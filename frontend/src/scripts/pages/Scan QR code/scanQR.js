(() => {
    const scanner = document.querySelector(".scanner");
    const video = document.getElementById("camera-preview");
    const cameraButton = document.getElementById("camera-button");
    const cameraButtonLabel = document.getElementById("camera-button-label");
    const galleryButton = document.getElementById("gallery-button");
    const galleryInput = document.getElementById("gallery-input");
    const status = document.getElementById("scan-status");
    const instruction = document.getElementById("scanner-instruction");

    let detector;
    let mediaStream;
    let scanTimer;
    let facingMode = "environment";
    let isDetecting = false;
    let canvas;
    let canvasContext;

    function setStatus(message, state) {
        status.textContent = message;

        if (state) {
            status.dataset.state = state;
        } else {
            delete status.dataset.state;
        }
    }

    function initializeDetector() {
        if ("BarcodeDetector" in window && !detector) {
            try {
                detector = new window.BarcodeDetector({
                    formats: ["qr_code"]
                });
            } catch (error) {
                console.warn(
                    "Native QR detection is unavailable; using the local decoder.",
                    error
                );
            }
        }

        if (!detector && typeof window.jsQR !== "function") {
            throw new Error("The QR decoder could not be loaded. Reload this page and try again.");
        }
    }

    async function decodeQr(source) {
        initializeDetector();

        if (detector) {
            const barcodes = await detector.detect(source);
            return barcodes[0]?.rawValue || "";
        }

        const width = source.videoWidth || source.width;
        const height = source.videoHeight || source.height;

        if (!width || !height) {
            return "";
        }

        if (!canvas) {
            canvas = document.createElement("canvas");
            canvasContext = canvas.getContext("2d", {
                willReadFrequently: true
            });
        }

        canvas.width = width;
        canvas.height = height;
        canvasContext.drawImage(source, 0, 0, width, height);

        const imageData = canvasContext.getImageData(0, 0, width, height);
        const result = window.jsQR(
            imageData.data,
            imageData.width,
            imageData.height,
            { inversionAttempts: "dontInvert" }
        );

        return result?.data || "";
    }

    function parseInvitation(value) {
        const text = value.trim();
        let roomCode = "";
        let pin = "";

        try {
            const payload = JSON.parse(text);

            if (payload && typeof payload === "object") {
                roomCode = String(
                    payload.roomCode || payload.room || payload.code || ""
                ).trim();
                pin = String(payload.pin || "").replace(/\D/g, "");
            }
        } catch {
            // QR payload may be a URL or a plain room code instead of JSON.
        }

        if (!roomCode) {
            try {
                const url = new URL(text);
                roomCode = (
                    url.searchParams.get("room") ||
                    url.searchParams.get("code") ||
                    ""
                ).trim();
                pin = (url.searchParams.get("pin") || pin)
                    .replace(/\D/g, "");
            } catch {
                // Handle a plain room code below.
            }
        }

        if (!roomCode && /^[a-z0-9][a-z0-9_-]{2,31}$/i.test(text)) {
            roomCode = text;
        }

        if (!/^[a-z0-9][a-z0-9_-]{2,31}$/i.test(roomCode)) {
            return null;
        }

        if (pin && !/^\d{6}$/.test(pin)) {
            return null;
        }

        return { roomCode, pin };
    }

    function stopCamera() {
        window.clearTimeout(scanTimer);
        scanTimer = undefined;
        isDetecting = false;

        if (mediaStream) {
            mediaStream.getTracks().forEach((track) => track.stop());
            mediaStream = undefined;
        }

        video.srcObject = null;
        scanner.classList.remove("is-active");
        cameraButtonLabel.textContent = "Start Camera";
    }

    function openJoinPage(invitation) {
        stopCamera();

        const joinUrl = new URL("../Join Room/join_room.html", window.location.href);
        joinUrl.searchParams.set("room", invitation.roomCode);

        if (invitation.pin) {
            joinUrl.searchParams.set("pin", invitation.pin);
        }

        setStatus("Room QR code scanned. Opening Join Room…", "success");
        window.location.assign(joinUrl.toString());
    }

    function handleResult(rawValue) {
        const invitation = parseInvitation(rawValue);

        if (!invitation) {
            setStatus(
                "That QR code does not contain a valid LocalLink room invitation.",
                "error"
            );
            return false;
        }

        openJoinPage(invitation);
        return true;
    }

    async function scanVideoFrame() {
        if (!mediaStream || isDetecting || video.readyState < HTMLMediaElement.HAVE_CURRENT_DATA) {
            scanTimer = window.setTimeout(scanVideoFrame, 150);
            return;
        }

        isDetecting = true;

        try {
            const rawValue = await decodeQr(video);

            if (rawValue && handleResult(rawValue)) {
                return;
            }
        } catch (error) {
            console.error("QR camera scan failed:", error);
            setStatus("The camera scan stopped unexpectedly. Try starting it again.", "error");
            stopCamera();
            return;
        } finally {
            isDetecting = false;
        }

        if (mediaStream) {
            scanTimer = window.setTimeout(scanVideoFrame, 150);
        }
    }

    async function startCamera() {
        try {
            initializeDetector();

            if (!navigator.mediaDevices?.getUserMedia) {
                throw new Error(
                    "Camera access is unavailable. Open this page over HTTPS or localhost."
                );
            }

            stopCamera();
            setStatus("Requesting camera access…");

            mediaStream = await navigator.mediaDevices.getUserMedia({
                audio: false,
                video: {
                    facingMode: { ideal: facingMode }
                }
            });

            video.srcObject = mediaStream;
            await video.play();
            scanner.classList.add("is-active");
            cameraButtonLabel.textContent = "Switch Camera";
            instruction.textContent = "Position the QR code within the frame";
            setStatus("Camera is ready. Point it at a LocalLink room QR code.");
            scanVideoFrame();
        } catch (error) {
            console.error("Unable to start QR camera:", error);

            if (mediaStream) {
                stopCamera();
            }

            setStatus(error.message || "Unable to start the camera.", "error");
        }
    }

    cameraButton.addEventListener("click", async () => {
        if (mediaStream) {
            facingMode = facingMode === "environment"
                ? "user"
                : "environment";
        }

        await startCamera();
    });

    galleryButton.addEventListener("click", () => {
        galleryInput.click();
    });

    galleryInput.addEventListener("change", async () => {
        const [imageFile] = galleryInput.files;

        if (!imageFile) {
            return;
        }

        let imageBitmap;

        try {
            initializeDetector();
            imageBitmap = await createImageBitmap(imageFile);
            const rawValue = await decodeQr(imageBitmap);

            if (!rawValue) {
                setStatus("No QR code was found in that image.", "error");
                return;
            }

            handleResult(rawValue);
        } catch (error) {
            console.error("Unable to scan the selected image:", error);
            setStatus(error.message || "Unable to scan that image.", "error");
        } finally {
            imageBitmap?.close();
            galleryInput.value = "";
        }
    });

    window.addEventListener("pagehide", stopCamera);
})();

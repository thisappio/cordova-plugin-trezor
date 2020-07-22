interface CordovaTrezor {
    getPublicKeys(request: PublicKeyRequest, successCallback: (PublicKeyResponse) => void, errorCallback: (error?: any) => void);
}

interface PublicKeyRequest {
    bundle: { path: string }[];
}

interface PublicKeyResponse {
    success: boolean;
    payload: PublicKey[] | ErrorPayload;
}

interface ErrorPayload {
    error: string;
}

interface PublicKey {
    path: number[];
    xpub: string;
    chainCode: string;
    childNum: number;
    publicKey: string;
    fingerprint: number;
    depth: number;
    xpubSegwit?: string;
}

declare var cordovaTrezor: CordovaTrezor;
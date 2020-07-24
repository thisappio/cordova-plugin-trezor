interface CordovaTrezor {
    getPublicKeys(request: PublicKeyRequest): Promise<PublicKeyResponse>;
    manifest(request: Manifest): void;
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

interface Manifest {
    email: string;
    appUrl: string;
}

declare var cordovaTrezor: CordovaTrezor;
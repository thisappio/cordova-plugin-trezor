function CordovaTrezor() { };

const _getHDPath = (path) => {
    const parts = path.toLowerCase().split('/');
    if (parts[0] !== 'm') throw 'Not a valid path';
    return parts.filter((p) => p !== 'm' && p !== '')
        .map((p) => {
            let hardened = false;
            if (p.substr(p.length - 1) === "'") {
                hardened = true;
                p = p.substr(0, p.length - 1);
            }
            let n = parseInt(p);
            if (isNaN(n)) {
                throw 'Not a valid path';
            } else if (n < 0) {
                throw 'Path cannot contain negative values';
            }
            if (hardened) { // hardened index
                n = (n | 0x80000000) >>> 0;
            }
            return n;
        });
};

const _convertPathToArray = (path) => {
    let valid = _getHDPath(path);
    if (!valid) throw 'Not a valid path';
    return valid;
};

CordovaTrezor.prototype.getPublicKeys = function (request) {
    let paths = request;
    if (cordova.platformId === 'android') {
        paths = { paths: request.bundle.map(p => _convertPathToArray(p.path)) };
    }

    return new Promise((resolve, reject) => {
        cordova.exec(resolve, reject, 'CordovaTrezor', 'getPublicKeys', [paths]);
    });
}

CordovaTrezor.prototype.manifest = function (request) {
    if (cordova.platformId === 'android') {
        console.log('"manifest" is not supported on Android');
        return;
    }

    cordova.exec(resolve, reject, 'CordovaTrezor', 'manifest', [request]);
}

module.exports = new CordovaTrezor();
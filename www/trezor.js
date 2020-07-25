function CordovaTrezor() { };

CordovaTrezor.prototype.getPublicKeys = function (request) {
    return new Promise((resolve, reject) => {
        cordova.exec(resolve, reject, 'CordovaTrezor', 'getPublicKeys', [request]);
    });
}

CordovaTrezor.prototype.manifest = function (request) {
    if (cordova.platformId === 'android') {
        console.log('"manifest" is not supported on Android');
        return;
    }

    cordova.exec(undefined, undefined, 'CordovaTrezor', 'manifest', [request]);
}

module.exports = new CordovaTrezor();
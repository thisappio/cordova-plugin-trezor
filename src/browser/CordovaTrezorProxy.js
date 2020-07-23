var TrezorConnect = require('trezor-connect').default;

function registerManifest(params) {
    TrezorConnect.manifest(params);
}

function getPublicKeys(params, success, error) {
    console.log(params);
    TrezorConnect.getPublicKey(params)
        .then(success)
        .catch((e) => {
            error(e);
            return Promise.reject();
        });
}

module.exports = {
    manifest: registerManifest,
    getPublicKeys: getPublicKeys
};

require('cordova/exec/proxy').add('CordovaTrezor', module.exports);
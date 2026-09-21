"""Resource-table packaging contract, checked on the final signed archive."""
import io, struct, zipfile

RESOURCE_ALIGNMENT=4096

def write_entry(archive,name,data):
    # Android R+ requires STORED resources. zipalign cannot fix DEFLATED data.
    archive.writestr(name,data,compress_type=zipfile.ZIP_STORED if name=='resources.arsc' else zipfile.ZIP_DEFLATED)

def inspect(data,alignment=RESOURCE_ALIGNMENT):
    source=io.BytesIO(data)
    with zipfile.ZipFile(source) as archive:
        item=archive.getinfo('resources.arsc')
        source.seek(item.header_offset)
        header=source.read(30)
        if header[:4]!=b'PK\x03\x04':raise AssertionError('Invalid ZIP local header')
        filename,extra=struct.unpack_from('<HH',header,26)
        offset=item.header_offset+30+filename+extra
        if item.compress_type!=zipfile.ZIP_STORED:raise AssertionError('resources.arsc must be uncompressed')
        if offset%alignment:raise AssertionError(f'resources.arsc offset {offset} is not {alignment}-byte aligned')
        if struct.unpack_from('<H',header,8)[0]!=zipfile.ZIP_STORED:raise AssertionError('Local ZIP compression differs')
        return {'compression':'STORED','data_offset':offset,'alignment':alignment,'bytes':item.file_size}

def verify_apk(path):
    report={'outer':inspect(path.read_bytes())}
    with zipfile.ZipFile(path) as archive:
        if 'assets/ui/editor-ui.apk' in archive.namelist():
            report['embedded_ui']=inspect(archive.read('assets/ui/editor-ui.apk'))
    return report

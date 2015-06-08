/*
 * Copyright (C) 2015 Federico Iosue (federico.iosue@gmail.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package it.feio.android.omninotes.async.notes;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import it.feio.android.omninotes.OmniNotes;
import it.feio.android.omninotes.db.DbHelper;
import it.feio.android.omninotes.models.Attachment;
import it.feio.android.omninotes.models.Note;
import it.feio.android.omninotes.models.listeners.OnNoteSaved;
import it.feio.android.omninotes.utils.Constants;
import it.feio.android.omninotes.utils.ReminderHelper;
import it.feio.android.omninotes.utils.StorageHelper;

import java.util.Calendar;
import java.util.List;


public class SaveNoteTask extends AsyncTask<Note, Void, Note> {

    private Context context;
    private boolean updateLastModification = true;
    private OnNoteSaved mOnNoteSaved;


    public SaveNoteTask(boolean updateLastModification) {
        this(null, updateLastModification);
    }


    public SaveNoteTask(OnNoteSaved mOnNoteSaved, boolean updateLastModification) {
        super();
        this.context = OmniNotes.getAppContext();
        this.mOnNoteSaved = mOnNoteSaved;
        this.updateLastModification = updateLastModification;
    }


    @Override
    protected Note doInBackground(Note... params) {
        Note note = params[0];
        purgeRemovedAttachments(note);
        note.setReminderFired(note.getAlarm() == null || Long.parseLong(note.getAlarm()) < Calendar.getInstance()
                .getTimeInMillis());
        note = DbHelper.getInstance().updateNote(note, updateLastModification);
        if (!note.isReminderFired()) {
            ReminderHelper.addReminder(context, note);
        }
        return note;
    }


    private void purgeRemovedAttachments(Note note) {
        List<Attachment> deletedAttachments = note.getAttachmentsListOld();
        for (Attachment attachment : note.getAttachmentsList()) {
            if (attachment.getId() != 0) {
                // Workaround to prevent deleting attachments if instance is changed (app restart)
                if (deletedAttachments.indexOf(attachment) == -1) {
                    attachment = getFixedAttachmentInstance(deletedAttachments, attachment);
                }
                deletedAttachments.remove(attachment);
            }
        }
        // Remove from database deleted attachments
        for (Attachment deletedAttachment : deletedAttachments) {
            StorageHelper.delete(context, deletedAttachment.getUri().getPath());
            Log.d(Constants.TAG, "Removed attachment " + deletedAttachment.getUri());
        }
    }


    private Attachment getFixedAttachmentInstance(List<Attachment> deletedAttachments, Attachment attachment) {
        for (Attachment deletedAttachment : deletedAttachments) {
            if (deletedAttachment.getId() == attachment.getId()) return deletedAttachment;
        }
        return attachment;
    }


    @Override
    protected void onPostExecute(Note note) {
        super.onPostExecute(note);
        if (this.mOnNoteSaved != null) {
            mOnNoteSaved.onNoteSaved(note);
        }
    }
}
